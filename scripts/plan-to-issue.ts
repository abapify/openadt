#!/usr/bin/env bun
/**
 * Sync Cursor plan files (.cursor/plans/*.plan.md) to GitHub issues.
 * Used locally (--file / --dry-run) and from .github/workflows/plan-sync.yml (--from-push).
 */
import { Octokit } from "@octokit/rest";
import { RequestError } from "@octokit/request-error";
import { readFileSync, readdirSync } from "node:fs";
import { basename, join, relative } from "node:path";

const PLAN_DIR_PREFIX = ".cursor/plans/";
const PLAN_ID_MARKER = "openadt-plan-id";
const CATEGORY_LABEL = "cursor-plan";

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

function unquoteYamlScalar(raw: string): string {
  const value = raw.trim();
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }
  return value;
}

export function parsePlanFrontmatter(
  frontmatter: string,
): Omit<ParsedPlan, "bodyMarkdown"> {
  const nameMatch = frontmatter.match(/^name:\s*(.+)$/m);
  if (!nameMatch) {
    throw new Error("Plan frontmatter missing name:");
  }

  const overviewMatch = frontmatter.match(/^overview:\s*(.+)$/m);
  if (!overviewMatch) {
    throw new Error("Plan frontmatter missing overview:");
  }

  const todos: PlanTodo[] = [];
  const lines = frontmatter.split("\n");
  let i = 0;
  while (i < lines.length) {
    const line = lines[i]!;
    const itemMatch = line.match(/^\s*-\s*id:\s*(.+)$/);
    if (!itemMatch) {
      i++;
      continue;
    }
    const todo: PlanTodo = {
      id: unquoteYamlScalar(itemMatch[1]!),
      content: "",
      status: "pending",
    };
    i++;
    while (i < lines.length && !/^\s*-\s*id:/.test(lines[i]!)) {
      const field = lines[i]!;
      const contentMatch = field.match(/^\s*content:\s*(.+)$/);
      const statusMatch = field.match(/^\s*status:\s*(.+)$/);
      if (contentMatch) {
        todo.content = unquoteYamlScalar(contentMatch[1]!);
      } else if (statusMatch) {
        todo.status = unquoteYamlScalar(statusMatch[1]!);
      }
      i++;
    }
    todos.push(todo);
  }

  return {
    name: unquoteYamlScalar(nameMatch[1]!),
    overview: unquoteYamlScalar(overviewMatch[1]!),
    todos,
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
  root = join(import.meta.dir, ".."),
): ParsedPlan {
  const content = readFileSync(absPath, "utf8");
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
  octokit: Octokit,
  repo: GitHubRepo,
  name: string,
  color = "1d76db",
): Promise<void> {
  try {
    await octokit.rest.issues.getLabel({
      owner: repo.owner,
      repo: repo.repo,
      name,
    });
  } catch (error) {
    if (!(error instanceof RequestError) || error.status !== 404) {
      throw error;
    }
    await octokit.rest.issues.createLabel({
      owner: repo.owner,
      repo: repo.repo,
      name,
      color,
      description: "Cursor plan sync",
    });
  }
}

async function findOpenIssueForPlan(
  octokit: Octokit,
  repo: GitHubRepo,
  planLabel: string,
): Promise<{ number: number } | undefined> {
  const { data: issues } = await octokit.rest.issues.listForRepo({
    owner: repo.owner,
    repo: repo.repo,
    state: "open",
    labels: `${CATEGORY_LABEL},${planLabel}`,
    per_page: 1,
  });
  return issues[0];
}

async function syncPlanFile(options: {
  absPath: string;
  root: string;
  octokit?: Octokit;
  repo: GitHubRepo;
  sha?: string;
  dryRun?: boolean;
}): Promise<{
  action: "created" | "skipped";
  issueNumber?: number;
  title: string;
}> {
  const relPath = relative(options.root, options.absPath).replace(/\\/g, "/");
  if (!relPath.startsWith(PLAN_DIR_PREFIX)) {
    throw new Error(`Plan path must be under ${PLAN_DIR_PREFIX}: ${relPath}`);
  }

  const plan = parsePlanFile(options.absPath, options.root);
  const planId = planIdFromRelPath(relPath);
  const planLabel = planLabelForId(planId);
  const title = `[Plan] ${plan.name}`;
  const blobUrl = options.sha
    ? `https://github.com/${options.repo.owner}/${options.repo.repo}/blob/${options.sha}/${relPath}`
    : `https://github.com/${options.repo.owner}/${options.repo.repo}/blob/HEAD/${relPath}`;
  const body = buildIssueBody({
    plan,
    relPath,
    planId,
    blobUrl,
    sha: options.sha,
  });

  if (options.dryRun) {
    console.log(`[dry-run] ${relPath}`);
    console.log(`  title: ${title}`);
    console.log(`  labels: ${CATEGORY_LABEL}, ${planLabel}`);
    console.log(body);
    return { action: "created", title };
  }

  if (!options.octokit) {
    throw new Error("Octokit client is required unless --dry-run");
  }
  const octokit = options.octokit;

  await ensureLabel(octokit, options.repo, CATEGORY_LABEL, "5319e7");
  await ensureLabel(octokit, options.repo, planLabel, "0e8a16");

  const existing = await findOpenIssueForPlan(octokit, options.repo, planLabel);

  if (existing) {
    console.log(
      `Skipping ${relPath}: open issue #${existing.number} already exists (plan updates are not synced yet)`,
    );
    return { action: "skipped", issueNumber: existing.number, title };
  }

  const { data: created } = await octokit.rest.issues.create({
    owner: options.repo.owner,
    repo: options.repo.repo,
    title,
    body,
    labels: [CATEGORY_LABEL, planLabel],
  });
  console.log(`Created issue #${created.number} for ${relPath}`);
  return { action: "created", issueNumber: created.number, title };
}

function listAllPlanFiles(root: string): string[] {
  const dir = join(root, PLAN_DIR_PREFIX);
  try {
    return readdirSync(dir)
      .filter((name) => name.endsWith(".plan.md"))
      .map((name) => `${PLAN_DIR_PREFIX}${name}`)
      .sort();
  } catch {
    return [];
  }
}

function changedPlanFilesFromPush(root: string): string[] {
  const before = process.env.GITHUB_EVENT_BEFORE;
  const sha = process.env.GITHUB_SHA;
  if (!sha) {
    throw new Error("GITHUB_SHA is required for --from-push");
  }

  const zeroSha = /^0+$/;
  const proc =
    before && !zeroSha.test(before)
      ? Bun.spawnSync(
          ["git", "diff", "--name-only", before, sha, "--", PLAN_DIR_PREFIX],
          { cwd: root, stdout: "pipe", stderr: "pipe" },
        )
      : Bun.spawnSync(
          [
            "git",
            "diff-tree",
            "--no-commit-id",
            "--name-only",
            "-r",
            sha,
            "--",
            PLAN_DIR_PREFIX,
          ],
          { cwd: root, stdout: "pipe", stderr: "pipe" },
        );

  if (proc.exitCode !== 0) {
    throw new Error(
      `git diff failed: ${new TextDecoder().decode(proc.stderr)}`,
    );
  }

  const changed = new TextDecoder()
    .decode(proc.stdout)
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.endsWith(".plan.md"));

  if (changed.length > 0) {
    return changed;
  }

  // workflow_dispatch or first push: sync every tracked plan file.
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
  GITHUB_EVENT_NAME — workflow_dispatch syncs all plans when diff is empty`);
  process.exit(2);
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const dryRun = args.includes("--dry-run");
  const fromPush = args.includes("--from-push");
  const syncAll = args.includes("--all");
  const fileFlag = args.indexOf("--file");
  const root = join(import.meta.dir, "..");

  let files: string[] = [];
  if (syncAll) {
    files = listAllPlanFiles(root);
  } else if (fromPush) {
    files = changedPlanFilesFromPush(root);
  } else if (fileFlag >= 0) {
    const file = args[fileFlag + 1];
    if (!file) {
      usage();
    }
    files = [relative(root, join(process.cwd(), file)).replace(/\\/g, "/")];
  } else {
    usage();
  }

  if (files.length === 0) {
    console.log("No changed Cursor plan files.");
    return;
  }

  const repo =
    dryRun && !process.env.GITHUB_REPOSITORY
      ? { owner: "owner", repo: "repo" }
      : repoFromEnv();
  const token = dryRun ? undefined : tokenFromEnv();
  const sha = process.env.GITHUB_SHA;
  const octokit = token ? createOctokit(token) : undefined;

  for (const relPath of files) {
    await syncPlanFile({
      absPath: join(root, relPath),
      root,
      octokit,
      repo,
      sha,
      dryRun,
    });
  }
}

if (import.meta.main) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : error);
    process.exit(1);
  });
}
