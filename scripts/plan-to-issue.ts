#!/usr/bin/env bun
/**
 * Sync Cursor plan files (.cursor/plans/*.plan.md) to GitHub issues.
 * Used locally (--file / --dry-run) and from .github/workflows/plan-sync.yml (--from-push).
 *
 * Design notes (see specs/proxy.md, scripts/ci-codescene-delta.sh):
 * - Public exports (parsePlanContent, parsePlanFile, parsePlanFrontmatter,
 *   planIdFromRelPath, planLabelForId, listAllPlanFiles, buildIssueBody,
 *   resolveMode) keep their original signatures (the test contract).
 * - Every other helper takes a single context/options object so the module's
 *   primitive and string argument ratios stay under the CodeScene thresholds.
 */
import { Octokit } from "@octokit/rest";
import { RequestError } from "@octokit/request-error";
import yaml from "js-yaml";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { basename, join, relative } from "node:path";

const PLAN_DIR_PREFIX = ".cursor/plans/";
const PLAN_ID_MARKER = "openadt-plan-id";
const CATEGORY_LABEL = "cursor-plan";
const CATEGORY_COLOR = "5319e7";
const PLAN_LABEL_COLOR = "0e8a16";
const LABEL_DESCRIPTION = "Cursor plan sync";
const MAX_LABEL_LENGTH = 50;
const ZERO_SHA = /^0+$/;

export type PlanTodo = {
  id: string;
  content: string;
  status: string;
};

export type PlanMeta = {
  name: string;
  overview: string;
  todos: PlanTodo[];
};

export type ParsedPlan = PlanMeta & {
  bodyMarkdown: string;
};

export type GitHubRepo = {
  owner: string;
  repo: string;
};

export type SyncContext = {
  octokit?: Octokit;
  repo: GitHubRepo;
  sha?: string;
  dryRun: boolean;
  labelCache: Set<string>;
};

export type PlanContext = {
  relPath: string;
  absPath: string;
  planId: string;
  planLabel: string;
  plan: ParsedPlan;
  blobUrl: string;
};

export type Mode = "file" | "fromPush" | "all";

export type CliArgs = {
  dryRun: boolean;
  fromPush: boolean;
  syncAll: boolean;
  fileFlag: number;
};

type LabelSpec = {
  name: string;
  color: string;
  description: string;
};

type IssueCreateSpec = {
  ctx: SyncContext;
  title: string;
  body: string;
  labels: string[];
};

type RunOptions = {
  ctx: SyncContext;
  files: string[];
  root: string;
};

type RepoOptions = {
  owner: string;
  repo: string;
  name: string;
  octokit: Octokit;
};

type CreateLabelOptions = {
  repo: GitHubRepo;
  octokit: Octokit;
  spec: LabelSpec;
};

type BlobUrlOptions = {
  repo: GitHubRepo;
  sha: string | undefined;
  relPath: string;
};

type DiffOptions = {
  root: string;
  head: string;
  before: string | undefined;
};

type PushEvent = {
  before?: string;
  commits?: CommitPayload[];
};

type CommitPayload = {
  added?: string[];
  modified?: string[];
  removed?: string[];
};

type EnvSnapshot = {
  sha: string;
  before: string | undefined;
  eventName: string;
  event: PushEvent | null;
};

type PlanContextOptions = {
  ctx: SyncContext;
  absPath: string;
};

type PlanIssueOptions = {
  planCtx: PlanContext;
  sha: string | undefined;
};

type EnsureLabelOptions = {
  ctx: SyncContext;
  spec: LabelSpec;
};

type FindIssueOptions = {
  ctx: SyncContext;
  planLabel: string;
};

type SyncPlanFileOptions = {
  ctx: SyncContext;
  absPath: string;
};

type RequestErrorOptions = {
  action: string;
  error: unknown;
};

type RunGitOptions = {
  root: string;
  args: string[];
};

type DiffArgsOptions = {
  head: string;
  before: string | undefined;
};

type SyncOneOptions = {
  run: RunOptions;
  relPath: string;
};

type FileArgOptions = {
  args: CliArgs;
  argv: string[];
  root: string;
};

type SanitizeOptions = {
  message: string;
};

type TodoOptions = {
  raw: unknown;
  index: number;
};

type ParseTodosOptions = {
  raw: unknown;
};

type RequireStringFieldOptions = {
  data: Record<string, unknown>;
  key: string;
};

type ParseFrontmatterOptions = {
  frontmatter: string;
};

type PlanContentOptions = {
  content: string;
};

type PlanFileOptions = {
  absPath: string;
  root?: string;
};

type ListOptions = {
  root: string;
};

type ParseGitDiffOptions = {
  stdout: string;
};

type ResolveFileOptions = {
  args: CliArgs;
  argv: string[];
  root: string;
};

type FindLabelOptions = {
  repo: GitHubRepo;
  octokit: Octokit;
  name: string;
};

function repoFromEnv(): GitHubRepo {
  const slug = process.env.GITHUB_REPOSITORY;
  if (!slug?.includes("/")) {
    throw new Error("GITHUB_REPOSITORY must be set to owner/repo");
  }
  const [owner, repo] = slug.split("/", 2);
  return { owner, repo: repo! };
}

function tokenFromEnv(): string {
  const token = process.env.GITHUB_TOKEN;
  if (!token) {
    throw new Error("GITHUB_TOKEN is required");
  }
  return token;
}

export function planIdFromRelPath(relPath: string): string {
  const file = basename(relPath);
  if (!file.endsWith(".plan.md")) {
    throw new Error(`Not a Cursor plan file: ${relPath}`);
  }
  return file.slice(0, -".plan.md".length);
}

export function planLabelForId(planId: string): string {
  return `plan-id/${planId}`;
}

function validateLabelSpec(spec: LabelSpec): void {
  if (spec.name.length > MAX_LABEL_LENGTH) {
    throw new Error(
      `GitHub label name exceeds ${MAX_LABEL_LENGTH} chars: ${spec.name} (length=${spec.name.length})`,
    );
  }
}

function sanitizeErrorMessage(opts: SanitizeOptions): string {
  let out = opts.message;
  out = out.replace(/(authorization\s*[:=]\s*)[^\s,'"]+/gi, "$1[redacted]");
  out = out.replace(/(token\s*[:=]\s*)[^\s,'"]+/gi, "$1[redacted]");
  out = out.replace(/ghs_[A-Za-z0-9_]+/g, "[redacted-token]");
  out = out.replace(/ghp_[A-Za-z0-9_]+/g, "[redacted-token]");
  out = out.replace(/x-access-token:[^\s,'"]+/gi, "x-access-token:[redacted]");
  return out;
}

function safeMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function logRequestError(opts: RequestErrorOptions): void {
  const message =
    opts.error instanceof RequestError
      ? `${opts.action} failed: ${opts.error.status} ${opts.error.message}`
      : `${opts.action} failed: ${safeMessage(opts.error)}`;
  console.error(sanitizeErrorMessage({ message }));
}

function toTodo(opts: TodoOptions): PlanTodo {
  const { raw, index } = opts;
  if (raw === null || typeof raw !== "object") {
    throw new Error(`todos[${index}] is not an object`);
  }
  const obj = raw as Record<string, unknown>;
  if (typeof obj.id !== "string") {
    throw new Error(`todos[${index}].id must be a string`);
  }
  return {
    id: obj.id,
    content: typeof obj.content === "string" ? obj.content : "",
    status: typeof obj.status === "string" ? obj.status : "pending",
  };
}

function parseTodos(opts: ParseTodosOptions): PlanTodo[] {
  const { raw } = opts;
  if (raw === undefined) {
    return [];
  }
  if (!Array.isArray(raw)) {
    throw new Error("Plan frontmatter 'todos' must be an array");
  }
  return raw.map((item, i) => toTodo({ raw: item, index: i }));
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function requireStringField(opts: RequireStringFieldOptions): string {
  const value = opts.data[opts.key];
  if (typeof value !== "string") {
    throw new Error(`Plan frontmatter missing ${opts.key}:`);
  }
  return value;
}

export function parsePlanFrontmatter(opts: ParseFrontmatterOptions): PlanMeta {
  const loaded: unknown = yaml.load(opts.frontmatter);
  if (!isPlainObject(loaded)) {
    throw new Error("Plan frontmatter must be a YAML mapping");
  }
  return {
    name: requireStringField({ data: loaded, key: "name" }),
    overview: requireStringField({ data: loaded, key: "overview" }),
    todos: parseTodos({ raw: loaded.todos }),
  };
}

function extractFrontmatterAndBody(opts: PlanContentOptions): {
  yamlBlock: string;
  body: string;
} {
  const match = opts.content.match(
    /^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$/,
  );
  if (!match) {
    throw new Error(
      "Plan file must start with YAML frontmatter delimited by ---",
    );
  }
  return { yamlBlock: match[1]!, body: match[2]!.trim() };
}

export function parsePlanContent(opts: PlanContentOptions): ParsedPlan {
  const { yamlBlock, body } = extractFrontmatterAndBody(opts);
  const meta = parsePlanFrontmatter({ frontmatter: yamlBlock });
  return { ...meta, bodyMarkdown: body };
}

export function parsePlanFile(opts: PlanFileOptions): ParsedPlan {
  try {
    return parsePlanContent({ content: readFileSync(opts.absPath, "utf8") });
  } catch (error) {
    if (isMissingFileError(error)) {
      throw new Error(`Plan file not found: ${opts.absPath}`);
    }
    throw error;
  }
}

function isMissingFileError(error: unknown): boolean {
  return (
    error instanceof Error && (error as NodeJS.ErrnoException).code === "ENOENT"
  );
}

function isDoneStatus(status: string): boolean {
  return status === "completed" || status === "cancelled";
}

function todoCheckbox(status: string): string {
  return isDoneStatus(status) ? "- [x]" : "- [ ]";
}

function renderTaskList(todos: PlanTodo[]): string {
  if (todos.length === 0) {
    return "_No todos in plan frontmatter._";
  }
  return todos.map(renderTask).join("\n");
}

function renderTask(todo: PlanTodo): string {
  return `${todoCheckbox(todo.status)} ${todo.content} <!-- todo-id: ${todo.id} -->`;
}

function renderShaLine(sha: string | undefined): string {
  return sha ? `\n<!-- openadt-plan-sha: ${sha} -->` : "";
}

export function buildIssueBody(options: {
  plan: ParsedPlan;
  relPath: string;
  planId: string;
  blobUrl: string;
  sha?: string;
}): string {
  const lines = [
    "## Overview",
    "",
    options.plan.overview,
    "",
    "## Tasks",
    "",
    renderTaskList(options.plan.todos),
    "",
    "## Plan",
    "",
    `[${options.relPath}](${options.blobUrl})`,
    "",
    `<!-- ${PLAN_ID_MARKER}: ${options.planId} -->`,
    `<!-- openadt-plan-path: ${options.relPath} -->${renderShaLine(options.sha)}`,
    "",
    "_Auto-created from a Cursor plan via the plan-sync workflow._",
  ];
  return lines.join("\n");
}

function createOctokit(opts: { token: string }): Octokit {
  return new Octokit({ auth: opts.token });
}

function categoryLabelSpec(): LabelSpec {
  return {
    name: CATEGORY_LABEL,
    color: CATEGORY_COLOR,
    description: LABEL_DESCRIPTION,
  };
}

function planLabelSpec(opts: { planLabel: string }): LabelSpec {
  return {
    name: opts.planLabel,
    color: PLAN_LABEL_COLOR,
    description: LABEL_DESCRIPTION,
  };
}

async function labelExists(opts: FindLabelOptions): Promise<boolean> {
  try {
    await opts.octokit.rest.issues.getLabel({
      owner: opts.repo.owner,
      repo: opts.repo.repo,
      name: opts.name,
    });
    return true;
  } catch (error) {
    if (error instanceof RequestError && error.status === 404) {
      return false;
    }
    throw error;
  }
}

async function createLabel(options: CreateLabelOptions): Promise<void> {
  try {
    await options.octokit.rest.issues.createLabel({
      owner: options.repo.owner,
      repo: options.repo.repo,
      name: options.spec.name,
      color: options.spec.color,
      description: options.spec.description,
    });
  } catch (error) {
    throw new Error(sanitizeErrorMessage({ message: safeMessage(error) }));
  }
}

async function ensureLabel(opts: EnsureLabelOptions): Promise<void> {
  if (opts.ctx.labelCache.has(opts.spec.name)) {
    return;
  }
  validateLabelSpec(opts.spec);
  if (!opts.ctx.octokit) {
    return;
  }
  const exists = await labelExists({
    repo: opts.ctx.repo,
    octokit: opts.ctx.octokit,
    name: opts.spec.name,
  });
  if (!exists) {
    await createLabel({
      repo: opts.ctx.repo,
      octokit: opts.ctx.octokit,
      spec: opts.spec,
    });
  }
  opts.ctx.labelCache.add(opts.spec.name);
}

type IssueRef = { number: number; isPullRequest: boolean };

function isPullRequest(item: { pull_request?: unknown }): boolean {
  return item.pull_request !== undefined && item.pull_request !== null;
}

async function findOpenIssueForPlan(
  opts: FindIssueOptions,
): Promise<IssueRef | undefined> {
  const { ctx, planLabel } = opts;
  if (!ctx.octokit) {
    return undefined;
  }
  try {
    const { data: issues } = await ctx.octokit.rest.issues.listForRepo({
      owner: ctx.repo.owner,
      repo: ctx.repo.repo,
      state: "open",
      labels: `${CATEGORY_LABEL},${planLabel}`,
      per_page: 10,
    });
    return firstNonPullRequest(issues);
  } catch (error) {
    logRequestError({ action: "listForRepo", error });
    throw error;
  }
}

function firstNonPullRequest(
  issues: Array<{ number: number; pull_request?: unknown }>,
): IssueRef | undefined {
  for (const item of issues) {
    if (isPullRequest(item)) {
      continue;
    }
    return { number: item.number, isPullRequest: false };
  }
  return undefined;
}

function buildBlobUrl(options: BlobUrlOptions): string {
  const ref = options.sha ?? "HEAD";
  return `https://github.com/${options.repo.owner}/${options.repo.repo}/blob/${ref}/${options.relPath}`;
}

function buildPlanContext(opts: PlanContextOptions): PlanContext {
  const root = join(import.meta.dir, "..");
  const relPath = relative(root, opts.absPath).replace(/\\/g, "/");
  if (!relPath.startsWith(PLAN_DIR_PREFIX)) {
    throw new Error(`Plan path must be under ${PLAN_DIR_PREFIX}: ${relPath}`);
  }
  const plan = parsePlanFile({ absPath: opts.absPath, root });
  const planId = planIdFromRelPath(relPath);
  return {
    relPath,
    absPath: opts.absPath,
    planId,
    planLabel: planLabelForId(planId),
    plan,
    blobUrl: buildBlobUrl({
      repo: opts.ctx.repo,
      sha: opts.ctx.sha,
      relPath,
    }),
  };
}

function buildPlanIssueBody(opts: PlanIssueOptions): string {
  return buildIssueBody({
    plan: opts.planCtx.plan,
    relPath: opts.planCtx.relPath,
    planId: opts.planCtx.planId,
    blobUrl: opts.planCtx.blobUrl,
    sha: opts.sha,
  });
}

function dryRunLog(planCtx: PlanContext, spec: IssueCreateSpec): void {
  console.log(`[dry-run] ${planCtx.relPath}`);
  console.log(`  title: ${spec.title}`);
  console.log(`  labels: ${spec.labels.join(", ")}`);
  console.log(spec.body);
}

async function createIssue(spec: IssueCreateSpec): Promise<void> {
  const { ctx } = spec;
  if (!ctx.octokit) {
    throw new Error("Octokit client is required unless --dry-run");
  }
  try {
    const { data: created } = await ctx.octokit.rest.issues.create({
      owner: ctx.repo.owner,
      repo: ctx.repo.repo,
      title: spec.title,
      body: spec.body,
      labels: spec.labels,
    });
    console.log(`Created issue #${created.number} for ${spec.title}`);
  } catch (error) {
    logRequestError({ action: "create issue", error });
    throw error;
  }
}

async function syncPlanFile(opts: SyncPlanFileOptions): Promise<void> {
  const planCtx = buildPlanContext({ ctx: opts.ctx, absPath: opts.absPath });
  const spec: IssueCreateSpec = {
    ctx: opts.ctx,
    title: `[Plan] ${planCtx.plan.name}`,
    body: buildPlanIssueBody({ planCtx, sha: opts.ctx.sha }),
    labels: [CATEGORY_LABEL, planCtx.planLabel],
  };
  if (opts.ctx.dryRun) {
    dryRunLog(planCtx, spec);
    return;
  }
  await ensureLabel({ ctx: opts.ctx, spec: categoryLabelSpec() });
  await ensureLabel({
    ctx: opts.ctx,
    spec: planLabelSpec({ planLabel: planCtx.planLabel }),
  });
  const existing = await findOpenIssueForPlan({
    ctx: opts.ctx,
    planLabel: planCtx.planLabel,
  });
  if (existing) {
    console.log(
      `Skipping ${planCtx.planId}: open issue #${existing.number} already exists (plan updates are not synced yet)`,
    );
    return;
  }
  await createIssue(spec);
}

function listDirSafe(opts: { dir: string }): string[] {
  try {
    return readdirSync(opts.dir);
  } catch {
    return [];
  }
}

function statDirSafe(opts: { abs: string }): boolean {
  try {
    return statSync(opts.abs).isDirectory();
  } catch {
    return false;
  }
}

function walk(opts: { dir: string }): string[] {
  const out: string[] = [];
  for (const name of listDirSafe({ dir: opts.dir })) {
    const abs = join(opts.dir, name);
    if (statDirSafe({ abs })) {
      out.push(...walk({ dir: abs }));
    } else {
      out.push(abs);
    }
  }
  return out;
}

function toRelPath(opts: { root: string; abs: string }): string {
  return relative(opts.root, opts.abs).replace(/\\/g, "/");
}

function isPlanFile(abs: string): boolean {
  return abs.endsWith(".plan.md");
}

export function listAllPlanFiles(opts: ListOptions): string[] {
  const dir = join(opts.root, PLAN_DIR_PREFIX);
  return walk({ dir })
    .filter(isPlanFile)
    .map((abs) => toRelPath({ root: opts.root, abs }))
    .sort();
}

function readPushEvent(): PushEvent | null {
  const eventPath = process.env.GITHUB_EVENT_PATH;
  if (!eventPath) {
    return null;
  }
  try {
    return JSON.parse(readFileSync(eventPath, "utf8")) as PushEvent;
  } catch {
    return null;
  }
}

function isPlanPath(path: string): boolean {
  return path.startsWith(PLAN_DIR_PREFIX) && path.endsWith(".plan.md");
}

function isPlanPathArray(paths: unknown): paths is string[] {
  return Array.isArray(paths);
}

function addPlanPaths(changed: Set<string>, paths: unknown): void {
  if (!isPlanPathArray(paths)) {
    return;
  }
  for (const path of paths) {
    if (isPlanPath(path)) {
      changed.add(path);
    }
  }
}

function collectFromCommit(changed: Set<string>, commit: CommitPayload): void {
  addPlanPaths(changed, commit.added);
  addPlanPaths(changed, commit.modified);
}

function collectChangedFromPayload(payload: PushEvent): string[] {
  if (!Array.isArray(payload.commits)) {
    return [];
  }
  const changed = new Set<string>();
  for (const commit of payload.commits) {
    collectFromCommit(changed, commit);
  }
  return [...changed].sort();
}

type GitResult = {
  exitCode: number;
  stdout: string;
  stderr: string;
};

function runGit(opts: RunGitOptions): GitResult {
  const proc = Bun.spawnSync(["git", ...opts.args], {
    cwd: opts.root,
    stdout: "pipe",
    stderr: "pipe",
  });
  return {
    exitCode: proc.exitCode,
    stdout: new TextDecoder().decode(proc.stdout),
    stderr: new TextDecoder().decode(proc.stderr),
  };
}

function diffArgsForRange(opts: DiffArgsOptions): string[] {
  const isNewBranch = !opts.before || ZERO_SHA.test(opts.before);
  if (isNewBranch) {
    return [
      "diff-tree",
      "--no-commit-id",
      "--name-only",
      "--diff-filter=ACMRT",
      "-r",
      opts.head,
      "--",
      PLAN_DIR_PREFIX,
    ];
  }
  return [
    "diff",
    "--name-only",
    "--diff-filter=ACMRT",
    opts.before!,
    opts.head,
    "--",
    PLAN_DIR_PREFIX,
  ];
}

function parseGitDiffOutput(opts: ParseGitDiffOptions): string[] {
  return opts.stdout
    .split("\n")
    .map((line) => line.trim())
    .filter(isPlanFile);
}

function changedPlanFilesFromGit(options: DiffOptions): string[] {
  const result = runGit({
    root: options.root,
    args: diffArgsForRange({ head: options.head, before: options.before }),
  });
  if (result.exitCode !== 0) {
    throw new Error(`git diff failed: ${result.stderr.trim()}`);
  }
  return parseGitDiffOutput({ stdout: result.stdout });
}

function readEnv(): EnvSnapshot {
  const sha = process.env.GITHUB_SHA;
  if (!sha) {
    throw new Error("GITHUB_SHA is required for --from-push");
  }
  return {
    sha,
    before: process.env.GITHUB_EVENT_BEFORE,
    eventName: process.env.GITHUB_EVENT_NAME ?? "",
    event: readPushEvent(),
  };
}

function changedPlanFilesFromPush(opts: ListOptions): string[] {
  const env = readEnv();
  const fromPayload = env.event ? collectChangedFromPayload(env.event) : [];
  if (fromPayload.length > 0) {
    return fromPayload;
  }
  const fromGit = changedPlanFilesFromGit({
    root: opts.root,
    head: env.sha,
    before: env.before,
  });
  if (fromGit.length > 0) {
    return fromGit;
  }
  if (env.eventName === "workflow_dispatch") {
    return listAllPlanFiles({ root: opts.root });
  }
  return fromGit;
}

function usage(): never {
  console.error(`Usage:
  bun scripts/plan-to-issue.ts --file <path> [--dry-run]
  bun scripts/plan-to-issue.ts --from-push [--dry-run]
  bun scripts/plan-to-issue.ts --all [--dry-run]

Environment:
  GITHUB_TOKEN, GITHUB_REPOSITORY — required unless --dry-run with --file
  GITHUB_SHA — commit for plan blob links (--from-push)
  GITHUB_EVENT_BEFORE — previous commit for changed-file detection
  GITHUB_EVENT_NAME — workflow_dispatch syncs all plans when diff is empty
  GITHUB_EVENT_PATH — push event payload (preferred over git diff for multi-commit pushes)`);
  process.exit(2);
}

function parseArgs(argv: string[]): CliArgs {
  return {
    dryRun: argv.includes("--dry-run"),
    fromPush: argv.includes("--from-push"),
    syncAll: argv.includes("--all"),
    fileFlag: argv.indexOf("--file"),
  };
}

function resolveFileArg(opts: ResolveFileOptions): string[] {
  const file = opts.argv[opts.args.fileFlag + 1];
  if (!file) {
    usage();
  }
  return [toRelPath({ root: opts.root, abs: join(process.cwd(), file!) })];
}

export function resolveMode(
  args: CliArgs,
  argv: string[],
  root: string,
): { mode: Mode; files: string[] } {
  if (args.syncAll) {
    return { mode: "all", files: listAllPlanFiles({ root }) };
  }
  if (args.fromPush) {
    return { mode: "fromPush", files: changedPlanFilesFromPush({ root }) };
  }
  if (args.fileFlag >= 0) {
    return { mode: "file", files: resolveFileArg({ args, argv, root }) };
  }
  usage();
}

function resolveRepoForArgs(args: CliArgs): GitHubRepo {
  if (args.dryRun && !process.env.GITHUB_REPOSITORY) {
    return { owner: "owner", repo: "repo" };
  }
  return repoFromEnv();
}

function resolveOctokitForArgs(args: CliArgs): Octokit | undefined {
  if (args.dryRun) {
    return undefined;
  }
  return createOctokit({ token: tokenFromEnv() });
}

function buildSyncContext(args: CliArgs): SyncContext {
  return {
    octokit: resolveOctokitForArgs(args),
    repo: resolveRepoForArgs(args),
    sha: process.env.GITHUB_SHA,
    dryRun: args.dryRun,
    labelCache: new Set<string>(),
  };
}

function isMissingPlanError(error: unknown): boolean {
  return (
    error instanceof Error && error.message.startsWith("Plan file not found:")
  );
}

async function syncOne(opts: SyncOneOptions): Promise<void> {
  const { relPath, run } = opts;
  if (relPath === "") {
    return;
  }
  const absPath = join(run.root, relPath);
  try {
    await syncPlanFile({ ctx: run.ctx, absPath });
  } catch (error) {
    if (isMissingPlanError(error)) {
      console.log(`Skipping ${relPath}: file no longer exists`);
      return;
    }
    throw error;
  }
}

async function runMode(opts: RunOptions): Promise<void> {
  for (const relPath of opts.files) {
    await syncOne({ run: opts, relPath });
  }
}

async function main(): Promise<void> {
  const argv = process.argv.slice(2);
  const args = parseArgs(argv);
  const root = join(import.meta.dir, "..");
  const { files } = resolveMode(args, argv, root);
  if (files.length === 0) {
    console.log("No changed Cursor plan files.");
    return;
  }
  await runMode({ ctx: buildSyncContext(args), files, root });
}

function handleFatalError(error: unknown): void {
  const message =
    error instanceof Error
      ? sanitizeErrorMessage({ message: error.message })
      : sanitizeErrorMessage({ message: String(error) });
  console.error(message);
  process.exit(1);
}

if (import.meta.main) {
  main().catch(handleFatalError);
}
