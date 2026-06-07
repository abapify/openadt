#!/usr/bin/env bun
/**
 * Sync Cursor plan files (.cursor/plans/*.plan.md) to GitHub issues.
 * Used locally (--file / --dry-run) and from .github/workflows/plan-sync.yml (--from-push).
 */
import { Octokit } from "@octokit/rest";
import { RequestError } from "@octokit/request-error";
import yaml from "js-yaml";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { basename, join, relative } from "node:path";

const PLAN_DIR_PREFIX = ".cursor/plans/";
const PLAN_ID_MARKER = "openadt-plan-id";
const CATEGORY_LABEL = "cursor-plan";
const MAX_LABEL_LENGTH = 50;
const ZERO_SHA = /^0+$/;

export type PlanTodo = {
  id: string;
  content: string;
  status: string;
};

export type ParsedPlan = {
  name: string;
  overview: string;
  todos: PlanTodo[];
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

function validateLabelName(name: string, relPath?: string): void {
  if (name.length > MAX_LABEL_LENGTH) {
    const where = relPath ? ` for ${relPath}` : "";
    throw new Error(
      `GitHub label name exceeds ${MAX_LABEL_LENGTH} chars${where}: ${name} (length=${name.length})`,
    );
  }
}

function sanitizeErrorMessage(message: string): string {
  let out = message;
  out = out.replace(/(authorization\s*[:=]\s*)[^\s,'"]+/gi, "$1[redacted]");
  out = out.replace(/(token\s*[:=]\s*)[^\s,'"]+/gi, "$1[redacted]");
  out = out.replace(/ghs_[A-Za-z0-9_]+/g, "[redacted-token]");
  out = out.replace(/ghp_[A-Za-z0-9_]+/g, "[redacted-token]");
  out = out.replace(/x-access-token:[^\s,'"]+/gi, "x-access-token:[redacted]");
  return out;
}

function toTodo(raw: unknown, index: number): PlanTodo {
  if (raw === null || typeof raw !== "object") {
    throw new Error(`todos[${index}] is not an object`);
  }
  const obj = raw as Record<string, unknown>;
  if (typeof obj.id !== "string") {
    throw new Error(`todos[${index}].id must be a string`);
  }
  const content = typeof obj.content === "string" ? obj.content : "";
  const status = typeof obj.status === "string" ? obj.status : "pending";
  return { id: obj.id, content, status };
}

function parseTodos(raw: unknown): PlanTodo[] {
  if (raw === undefined) {
    return [];
  }
  if (!Array.isArray(raw)) {
    throw new Error("Plan frontmatter 'todos' must be an array");
  }
  return raw.map((item, i) => toTodo(item, i));
}

function requireStringField(
  data: Record<string, unknown>,
  key: string,
): string {
  const value = data[key];
  if (typeof value !== "string") {
    throw new Error(`Plan frontmatter missing ${key}:`);
  }
  return value;
}

export function parsePlanFrontmatter(
  frontmatter: string,
): Omit<ParsedPlan, "bodyMarkdown"> {
  const loaded = yaml.load(frontmatter);
  if (loaded === null || typeof loaded !== "object" || Array.isArray(loaded)) {
    throw new Error("Plan frontmatter must be a YAML mapping");
  }
  const data = loaded as Record<string, unknown>;
  return {
    name: requireStringField(data, "name"),
    overview: requireStringField(data, "overview"),
    todos: parseTodos(data.todos),
  };
}

export function parsePlanContent(content: string): ParsedPlan {
  const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$/);
  if (!match) {
    throw new Error(
      "Plan file must start with YAML frontmatter delimited by ---",
    );
  }
  const parsed = parsePlanFrontmatter(match[1]!);
  return { ...parsed, bodyMarkdown: match[2]!.trim() };
}

export function parsePlanFile(
  absPath: string,
  _root = join(import.meta.dir, ".."),
): ParsedPlan {
  let content: string;
  try {
    content = readFileSync(absPath, "utf8");
  } catch (error) {
    if (
      error instanceof Error &&
      (error as NodeJS.ErrnoException).code === "ENOENT"
    ) {
      throw new Error(`Plan file not found: ${absPath}`);
    }
    throw error;
  }
  return parsePlanContent(content);
}

function todoCheckbox(status: string): string {
  return status === "completed" || status === "cancelled" ? "- [x]" : "- [ ]";
}

export function buildIssueBody(options: {
  plan: ParsedPlan;
  relPath: string;
  planId: string;
  blobUrl: string;
  sha?: string;
}): string {
  const tasks =
    options.plan.todos.length === 0
      ? "_No todos in plan frontmatter._"
      : options.plan.todos
          .map(
            (todo) =>
              `${todoCheckbox(todo.status)} ${todo.content} <!-- todo-id: ${todo.id} -->`,
          )
          .join("\n");

  const shaLine = options.sha
    ? `\n<!-- openadt-plan-sha: ${options.sha} -->`
    : "";

  return [
    "## Overview",
    "",
    options.plan.overview,
    "",
    "## Tasks",
    "",
    tasks,
    "",
    "## Plan",
    "",
    `[${options.relPath}](${options.blobUrl})`,
    "",
    `<!-- ${PLAN_ID_MARKER}: ${options.planId} -->`,
    `<!-- openadt-plan-path: ${options.relPath} -->${shaLine}`,
    "",
    "_Auto-created from a Cursor plan via the plan-sync workflow._",
  ].join("\n");
}

function createOctokit(token: string): Octokit {
  return new Octokit({ auth: token });
}

async function ensureLabel(
  ctx: SyncContext,
  name: string,
  color = "1d76db",
  relPath?: string,
): Promise<void> {
  if (ctx.labelCache.has(name)) {
    return;
  }
  validateLabelName(name, relPath);
  const { octokit, repo } = ctx;
  if (!octokit) {
    return;
  }
  try {
    await octokit.rest.issues.getLabel({
      owner: repo.owner,
      repo: repo.repo,
      name,
    });
    ctx.labelCache.add(name);
  } catch (error) {
    if (!(error instanceof RequestError) || error.status !== 404) {
      throw error;
    }
    try {
      await octokit.rest.issues.createLabel({
        owner: repo.owner,
        repo: repo.repo,
        name,
        color,
        description: "Cursor plan sync",
      });
      ctx.labelCache.add(name);
    } catch (createError) {
      throw new Error(sanitizeErrorMessage(safeMessage(createError)));
    }
  }
}

function safeMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function logRequestError(action: string, error: unknown): void {
  const message =
    error instanceof RequestError
      ? `${action} failed: ${error.status} ${error.message}`
      : `${action} failed: ${safeMessage(error)}`;
  console.error(sanitizeErrorMessage(message));
}

type IssueRef = { number: number; isPullRequest: boolean };

function isPullRequest(item: { pull_request?: unknown }): boolean {
  return item.pull_request !== undefined && item.pull_request !== null;
}

async function findOpenIssueForPlan(
  ctx: SyncContext,
  planLabel: string,
): Promise<IssueRef | undefined> {
  const { octokit, repo } = ctx;
  if (!octokit) {
    return undefined;
  }
  try {
    const { data: issues } = await octokit.rest.issues.listForRepo({
      owner: repo.owner,
      repo: repo.repo,
      state: "open",
      labels: `${CATEGORY_LABEL},${planLabel}`,
      per_page: 10,
    });
    for (const item of issues) {
      if (isPullRequest(item)) {
        continue;
      }
      return { number: item.number, isPullRequest: false };
    }
    return undefined;
  } catch (error) {
    logRequestError("listForRepo", error);
    throw error;
  }
}

function buildBlobUrl(ctx: SyncContext, relPath: string): string {
  const { owner, repo } = ctx.repo;
  const ref = ctx.sha ?? "HEAD";
  return `https://github.com/${owner}/${repo}/blob/${ref}/${relPath}`;
}

function buildPlanContext(ctx: SyncContext, absPath: string): PlanContext {
  const root = join(import.meta.dir, "..");
  const relPath = relative(root, absPath).replace(/\\/g, "/");
  if (!relPath.startsWith(PLAN_DIR_PREFIX)) {
    throw new Error(`Plan path must be under ${PLAN_DIR_PREFIX}: ${relPath}`);
  }
  const plan = parsePlanFile(absPath, root);
  const planId = planIdFromRelPath(relPath);
  const planLabel = planLabelForId(planId);
  return {
    relPath,
    absPath,
    planId,
    planLabel,
    plan,
    blobUrl: buildBlobUrl(ctx, relPath),
  };
}

async function syncPlanFile(ctx: SyncContext, absPath: string): Promise<void> {
  const planCtx = buildPlanContext(ctx, absPath);
  const title = `[Plan] ${planCtx.plan.name}`;
  const body = buildIssueBody({
    plan: planCtx.plan,
    relPath: planCtx.relPath,
    planId: planCtx.planId,
    blobUrl: planCtx.blobUrl,
    sha: ctx.sha,
  });

  if (ctx.dryRun) {
    console.log(`[dry-run] ${planCtx.relPath}`);
    console.log(`  title: ${title}`);
    console.log(`  labels: ${CATEGORY_LABEL}, ${planCtx.planLabel}`);
    console.log(body);
    return;
  }

  if (!ctx.octokit) {
    throw new Error("Octokit client is required unless --dry-run");
  }

  await ensureLabel(ctx, CATEGORY_LABEL, "5319e7", planCtx.relPath);
  await ensureLabel(ctx, planCtx.planLabel, "0e8a16", planCtx.relPath);

  const existing = await findOpenIssueForPlan(ctx, planCtx.planLabel);
  if (existing) {
    console.log(
      `Skipping ${planCtx.relPath}: open issue #${existing.number} already exists (plan updates are not synced yet)`,
    );
    return;
  }

  try {
    const { data: created } = await ctx.octokit.rest.issues.create({
      owner: ctx.repo.owner,
      repo: ctx.repo.repo,
      title,
      body,
      labels: [CATEGORY_LABEL, planCtx.planLabel],
    });
    console.log(`Created issue #${created.number} for ${planCtx.relPath}`);
  } catch (error) {
    logRequestError("create issue", error);
    throw error;
  }
}

function walk(dir: string): string[] {
  let entries: string[];
  try {
    entries = readdirSync(dir);
  } catch {
    return [];
  }
  const out: string[] = [];
  for (const name of entries) {
    const abs = join(dir, name);
    let isDir = false;
    try {
      isDir = statSync(abs).isDirectory();
    } catch {
      continue;
    }
    if (isDir) {
      out.push(...walk(abs));
    } else {
      out.push(abs);
    }
  }
  return out;
}

export function listAllPlanFiles(root: string): string[] {
  const dir = join(root, PLAN_DIR_PREFIX);
  return walk(dir)
    .filter((abs) => abs.endsWith(".plan.md"))
    .map((abs) => relative(root, abs).replace(/\\/g, "/"))
    .sort();
}

type PushPayload = {
  before?: string;
  commits?: Array<{
    added?: string[];
    modified?: string[];
    removed?: string[];
  }>;
};

function readPushEvent(): PushPayload | undefined {
  const eventPath = process.env.GITHUB_EVENT_PATH;
  if (!eventPath) {
    return undefined;
  }
  try {
    const raw = readFileSync(eventPath, "utf8");
    const parsed = JSON.parse(raw) as PushPayload;
    return parsed;
  } catch {
    return undefined;
  }
}

function collectChangedFromPayload(payload: PushPayload): string[] {
  if (!Array.isArray(payload.commits)) {
    return [];
  }
  const changed = new Set<string>();
  for (const commit of payload.commits) {
    for (const arr of [commit.added, commit.modified]) {
      if (Array.isArray(arr)) {
        for (const path of arr) {
          if (path.startsWith(PLAN_DIR_PREFIX) && path.endsWith(".plan.md")) {
            changed.add(path);
          }
        }
      }
    }
  }
  return [...changed].sort();
}

function runGit(
  root: string,
  args: string[],
): { exitCode: number; stdout: string; stderr: string } {
  const proc = Bun.spawnSync(["git", ...args], {
    cwd: root,
    stdout: "pipe",
    stderr: "pipe",
  });
  return {
    exitCode: proc.exitCode,
    stdout: new TextDecoder().decode(proc.stdout),
    stderr: new TextDecoder().decode(proc.stderr),
  };
}

export function changedPlanFilesFromPush(root: string): string[] {
  const sha = process.env.GITHUB_SHA;
  if (!sha) {
    throw new Error("GITHUB_SHA is required for --from-push");
  }

  const before = process.env.GITHUB_EVENT_BEFORE;
  const payload = readPushEvent();
  let fromPayload: string[] = [];
  if (payload) {
    fromPayload = collectChangedFromPayload(payload);
    if (fromPayload.length > 0) {
      return fromPayload;
    }
  }

  const isNewBranch = !before || ZERO_SHA.test(before);
  const diffArgs = isNewBranch
    ? [
        "diff-tree",
        "--no-commit-id",
        "--name-only",
        "--diff-filter=ACMRT",
        "-r",
        sha,
        "--",
        PLAN_DIR_PREFIX,
      ]
    : [
        "diff",
        "--name-only",
        "--diff-filter=ACMRT",
        before!,
        sha,
        "--",
        PLAN_DIR_PREFIX,
      ];

  const result = runGit(root, diffArgs);
  if (result.exitCode !== 0) {
    throw new Error(`git diff failed: ${result.stderr.trim()}`);
  }

  const changed = result.stdout
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.endsWith(".plan.md"));

  if (changed.length > 0) {
    return changed;
  }

  if (process.env.GITHUB_EVENT_NAME === "workflow_dispatch") {
    return listAllPlanFiles(root);
  }

  return changed;
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

function resolveFileArg(args: CliArgs, argv: string[], root: string): string[] {
  const file = argv[args.fileFlag + 1];
  if (!file) {
    usage();
  }
  return [relative(root, join(process.cwd(), file!)).replace(/\\/g, "/")];
}

export function resolveMode(
  args: CliArgs,
  argv: string[],
  root: string,
): { mode: Mode; files: string[] } {
  if (args.syncAll) {
    return { mode: "all", files: listAllPlanFiles(root) };
  }
  if (args.fromPush) {
    return { mode: "fromPush", files: changedPlanFilesFromPush(root) };
  }
  if (args.fileFlag >= 0) {
    return { mode: "file", files: resolveFileArg(args, argv, root) };
  }
  usage();
}

function buildSyncContext(args: CliArgs, root: string): SyncContext {
  const repo =
    args.dryRun && !process.env.GITHUB_REPOSITORY
      ? { owner: "owner", repo: "repo" }
      : repoFromEnv();
  const token = args.dryRun ? undefined : tokenFromEnv();
  return {
    octokit: token ? createOctokit(token) : undefined,
    repo,
    sha: process.env.GITHUB_SHA,
    dryRun: args.dryRun,
    labelCache: new Set<string>(),
  };
}

async function runMode(
  ctx: SyncContext,
  files: string[],
  root: string,
): Promise<void> {
  for (const relPath of files) {
    const absPath = join(root, relPath);
    if (relPath === "") {
      continue;
    }
    try {
      await syncPlanFile(ctx, absPath);
    } catch (error) {
      if (
        error instanceof Error &&
        error.message.startsWith("Plan file not found:")
      ) {
        console.log(`Skipping ${relPath}: file no longer exists`);
        continue;
      }
      throw error;
    }
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

  const ctx = buildSyncContext(args, root);
  await runMode(ctx, files, root);
}

if (import.meta.main) {
  main().catch((error) => {
    const message =
      error instanceof Error
        ? sanitizeErrorMessage(error.message)
        : sanitizeErrorMessage(String(error));
    console.error(message);
    process.exit(1);
  });
}
