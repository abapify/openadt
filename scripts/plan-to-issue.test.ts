import { describe, expect, test } from "bun:test";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  buildIssueBody,
  listAllPlanFiles,
  parsePlanContent,
  parsePlanFile,
  parsePlanFrontmatter,
  planIdFromRelPath,
  planLabelForId,
  resolveMode,
} from "./plan-to-issue.ts";

const sampleFrontmatter = `name: MCP shared backend EN
overview: "English implementation plan: auto-ensure MCP shared HTTP backend."
todos:
  - id: spec-mcp-shared-backend
    content: Write specs/mcp-shared-backend.md
    status: pending
  - id: docs-verify
    content: Update docs/usage.md
    status: completed
isProject: false`;

const fixtureFrontmatter = `name: "Escaped \\"quoted\\" name"
overview: 'Single-quoted with ''apostrophe'' inside'
todos:
  - id: alpha
    content: "Line one\\nLine two"
    status: pending
  - id: beta
    content: 'Tab\\there'
    status: completed
isProject: false`;

describe("planIdFromRelPath", () => {
  test("strips .plan.md suffix", () => {
    expect(
      planIdFromRelPath(".cursor/plans/mcp_shared_backend_en_5e8e195f.plan.md"),
    ).toBe("mcp_shared_backend_en_5e8e195f");
  });

  test("rejects non-plan paths", () => {
    expect(() => planIdFromRelPath(".cursor/plans/README.md")).toThrow(
      "Not a Cursor plan file",
    );
  });
});

describe("planLabelForId", () => {
  test("uses stable plan-id label", () => {
    expect(planLabelForId("mcp_shared_backend_en_5e8e195f")).toBe(
      "plan-id/mcp_shared_backend_en_5e8e195f",
    );
  });
});

describe("parsePlanFrontmatter", () => {
  test("reads name, overview, and todos", () => {
    const parsed = parsePlanFrontmatter({ frontmatter: sampleFrontmatter });
    expect(parsed.name).toBe("MCP shared backend EN");
    expect(parsed.overview).toContain("auto-ensure MCP shared HTTP backend");
    expect(parsed.todos).toHaveLength(2);
    expect(parsed.todos[0]).toEqual({
      id: "spec-mcp-shared-backend",
      content: "Write specs/mcp-shared-backend.md",
      status: "pending",
    });
    expect(parsed.todos[1]?.status).toBe("completed");
  });

  test("terminates todo block at next root-level key (isProject)", () => {
    const parsed = parsePlanFrontmatter({ frontmatter: sampleFrontmatter });
    // isProject is a root-level key and should NOT have been swallowed
    // into a todo entry; the parsed todos count is still 2.
    expect(parsed.todos).toHaveLength(2);
  });

  test("preserves escaped sequences and doubled single quotes via js-yaml", () => {
    const parsed = parsePlanFrontmatter({ frontmatter: fixtureFrontmatter });
    expect(parsed.name).toBe('Escaped "quoted" name');
    expect(parsed.overview).toBe("Single-quoted with 'apostrophe' inside");
    expect(parsed.todos[0]?.content).toBe("Line one\nLine two");
    // Single-quoted YAML scalars do not interpret escape sequences.
    expect(parsed.todos[1]?.content).toBe("Tab\\there");
  });

  test("throws on missing name", () => {
    expect(() =>
      parsePlanFrontmatter({ frontmatter: "overview: x\n" }),
    ).toThrow(/missing name/);
  });

  test("returns empty todos when field is absent", () => {
    const parsed = parsePlanFrontmatter({
      frontmatter: "name: x\noverview: y\n",
    });
    expect(parsed.todos).toEqual([]);
  });

  test("rejects todos that are not an array", () => {
    expect(() =>
      parsePlanFrontmatter({
        frontmatter: "name: x\noverview: y\ntodos: oops\n",
      }),
    ).toThrow(/todos/);
  });
});

describe("parsePlanContent", () => {
  test("parses tracked Cursor plan fixture", () => {
    const fixture = join(
      import.meta.dir,
      "../.cursor/plans/mcp_shared_backend_en_5e8e195f.plan.md",
    );
    const parsed = parsePlanContent({ content: readFileSync0(fixture) });
    expect(parsed.name).toBe("MCP shared backend EN");
    expect(parsed.todos.length).toBeGreaterThan(0);
    expect(parsed.bodyMarkdown).toContain("# MCP shared backend");
  });

  test("throws a clear error when the file does not exist", () => {
    const dir = mkdtempSync(join(tmpdir(), "plan-to-issue-"));
    try {
      expect(() =>
        parsePlanFile({ absPath: join(dir, "missing.plan.md"), root: dir }),
      ).toThrow(/Plan file not found/);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });
});

describe("buildIssueBody", () => {
  test("includes overview, tasks, markers, and blob link", () => {
    const plan = parsePlanFrontmatter({ frontmatter: sampleFrontmatter });
    const body = buildIssueBody({
      plan: { ...plan, bodyMarkdown: "# Body" },
      relPath: ".cursor/plans/mcp_shared_backend_en_5e8e195f.plan.md",
      planId: "mcp_shared_backend_en_5e8e195f",
      blobUrl:
        "https://github.com/org/repo/blob/sha/.cursor/plans/mcp_shared_backend_en_5e8e195f.plan.md",
      sha: "abc123",
    });

    expect(body).toContain("## Overview");
    expect(body).toContain("- [ ] Write specs/mcp-shared-backend.md");
    expect(body).toContain("- [x] Update docs/usage.md");
    expect(body).toContain(
      "<!-- openadt-plan-id: mcp_shared_backend_en_5e8e195f -->",
    );
    expect(body).toContain("<!-- openadt-plan-sha: abc123 -->");
  });
});

describe("listAllPlanFiles (recursive)", () => {
  test("finds plan files in nested subdirectories", () => {
    const root = mkdtempSync(join(tmpdir(), "plan-to-issue-walk-"));
    try {
      const plansDir = join(root, ".cursor", "plans");
      mkdirSync(join(plansDir, "nested", "deep"), { recursive: true });
      writeFileSync(
        join(plansDir, "top.plan.md"),
        "---\nname: top\noverview: o\n---\nbody",
      );
      writeFileSync(
        join(plansDir, "nested", "mid.plan.md"),
        "---\nname: mid\noverview: o\n---\nbody",
      );
      writeFileSync(
        join(plansDir, "nested", "deep", "deep.plan.md"),
        "---\nname: deep\noverview: o\n---\nbody",
      );
      writeFileSync(join(plansDir, "ignored.txt"), "not a plan");

      const files = listAllPlanFiles({ root });
      expect(files).toContain(".cursor/plans/top.plan.md");
      expect(files).toContain(".cursor/plans/nested/mid.plan.md");
      expect(files).toContain(".cursor/plans/nested/deep/deep.plan.md");
      expect(files).not.toContain(".cursor/plans/ignored.txt");
      expect(files).toHaveLength(3);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  test("returns an empty list when the plan directory is missing", () => {
    const root = mkdtempSync(join(tmpdir(), "plan-to-issue-walk-"));
    try {
      expect(listAllPlanFiles({ root })).toEqual([]);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

describe("resolveMode", () => {
  test("rejects unknown invocation with usage()", () => {
    const root = mkdtempSync(join(tmpdir(), "plan-to-issue-mode-"));
    try {
      const origExit = process.exit;
      const origErr = console.error;
      let exited = 0;
      process.exit = ((code?: number) => {
        exited = code ?? 0;
        throw new Error("__exit__");
      }) as typeof process.exit;
      console.error = () => {};
      try {
        expect(() =>
          resolveMode(
            { dryRun: false, fromPush: false, syncAll: false, fileFlag: -1 },
            [],
            root,
          ),
        ).toThrow("__exit__");
        expect(exited).toBe(2);
      } finally {
        process.exit = origExit;
        console.error = origErr;
      }
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});

function readFileSync0(p: string): string {
  // tiny wrapper so we can import the symbol under a unique name in the file
  // (kept inline to avoid an extra import in the test suite)
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const { readFileSync } = require("node:fs") as typeof import("node:fs");
  return readFileSync(p, "utf8");
}
