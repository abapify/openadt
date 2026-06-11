/**
 * GitHub CLI helpers for review-debt harvest.
 */
import type { ReviewThreadNode } from "./review-debt-lib.ts";

export function gh(args: string[]): string {
  const proc = Bun.spawnSync(["gh", ...args], {
    stdout: "pipe",
    stderr: "pipe",
  });
  if (proc.exitCode !== 0) {
    const err = new TextDecoder().decode(proc.stderr).trim();
    throw new Error(`gh ${args[0]} failed: ${err}`);
  }
  return new TextDecoder().decode(proc.stdout);
}

export function ensureGhAuth(): void {
  const proc = Bun.spawnSync(["gh", "auth", "status"], {
    stdout: "ignore",
    stderr: "ignore",
  });
  if (proc.exitCode !== 0) {
    console.error("error: gh not authenticated");
    process.exit(1);
  }
}

export interface GhRepoTarget {
  owner: string;
  repo: string;
}

export function resolveGhRepo(): GhRepoTarget {
  const slug = process.env.GITHUB_REPOSITORY;
  if (slug) {
    const [owner, repo] = slug.split("/");
    if (owner && repo) {
      return { owner, repo };
    }
  }
  const proc = Bun.spawnSync(
    "gh",
    ["repo", "view", "--json", "owner,name", "-q", '.owner.login + " " + .name'],
    { stdout: "pipe", stderr: "pipe" },
  );
  if (proc.exitCode !== 0) {
    const err = new TextDecoder().decode(proc.stderr).trim();
    throw new Error(
      `Could not resolve GitHub owner/repo: ${err || "gh repo view failed"}. ` +
        "Run from a gh-authenticated clone, set GITHUB_REPOSITORY, or pass OWNER REPO explicitly.",
    );
  }
  const [owner, repo] = new TextDecoder().decode(proc.stdout).trim().split(/\s+/);
  if (!owner || !repo) {
    throw new Error(
      "Could not resolve GitHub owner/repo. Run from a gh-authenticated clone, set GITHUB_REPOSITORY, or pass OWNER REPO explicitly.",
    );
  }
  return { owner, repo };
}

export interface GhPrTarget {
  owner: string;
  repo: string;
  pr: number;
}

interface ThreadPage {
  nodes: ReviewThreadNode[];
  pageInfo: { hasNextPage: boolean; endCursor: string | null };
}

function escapeGraphqlCursor(cursor: string): string {
  return cursor.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

function reviewThreadsQuery(afterClause: string): string {
  return `
    query($o: String!, $r: String!, $pr: Int!, $n: Int!) {
      repository(owner: $o, name: $r) {
        pullRequest(number: $pr) {
          reviewThreads(first: $n${afterClause}) {
            pageInfo { hasNextPage endCursor }
            nodes {
              id
              isResolved
              isOutdated
              comments(first: 1) {
                nodes { author { login } path line body }
              }
            }
          }
        }
      }
    }`;
}

function parseThreadPage(raw: string, pr: number): ThreadPage {
  const parsed = JSON.parse(raw) as {
    data?: {
      repository?: {
        pullRequest?: { reviewThreads?: ThreadPage };
      };
    };
    errors?: unknown;
  };

  if (parsed.errors) {
    throw new Error(`GraphQL errors: ${JSON.stringify(parsed.errors)}`);
  }

  const threads = parsed.data?.repository?.pullRequest?.reviewThreads;
  if (!threads) {
    throw new Error(`pull request #${pr} not found`);
  }

  return threads;
}

function fetchThreadPage(target: GhPrTarget, cursor: string): ThreadPage {
  const afterClause = cursor
    ? `, after: "${escapeGraphqlCursor(cursor)}"`
    : "";
  const raw = gh([
    "api",
    "graphql",
    "-f",
    `query=${reviewThreadsQuery(afterClause)}`,
    "-f",
    `o=${target.owner}`,
    "-f",
    `r=${target.repo}`,
    "-F",
    `pr=${target.pr}`,
    "-F",
    "n=100",
  ]);
  return parseThreadPage(raw, target.pr);
}

function nextPageCursor(page: ThreadPage): string | null {
  if (!page.pageInfo.hasNextPage) {
    return null;
  }
  return page.pageInfo.endCursor;
}

export async function fetchReviewThreads(
  target: GhPrTarget,
): Promise<ReviewThreadNode[]> {
  const nodes: ReviewThreadNode[] = [];
  let cursor = "";

  for (;;) {
    const page = fetchThreadPage(target, cursor);
    nodes.push(...page.nodes);
    const next = nextPageCursor(page);
    if (!next) {
      break;
    }
    cursor = next;
  }

  return nodes;
}
