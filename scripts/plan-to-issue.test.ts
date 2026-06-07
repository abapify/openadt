import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import {
  buildIssueBody,
  parsePlanContent,
  parsePlanFrontmatter,
  planIdFromRelPath,
  planLabelForId,
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

describe("planIdFromRelPath", () => {
  test("strips .plan.md suffix", () => {
    expect(
      planIdFromRelPath(".cursor/plans/mcp_shared_backend_en_5e8e195f.plan.md"),
    ).toBe("mcp_shared_backend_en_5e8e195f");
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
    const parsed = parsePlanFrontmatter(sampleFrontmatter);
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
});

describe("parsePlanContent", () => {
  test("parses tracked Cursor plan fixture", () => {
    const fixture = join(
      import.meta.dir,
      "../.cursor/plans/mcp_shared_backend_en_5e8e195f.plan.md",
    );
    const parsed = parsePlanContent(readFileSync(fixture, "utf8"));
    expect(parsed.name).toBe("MCP shared backend EN");
    expect(parsed.todos.length).toBeGreaterThan(0);
    expect(parsed.bodyMarkdown).toContain("# MCP shared backend");
  });
});

describe("buildIssueBody", () => {
  test("includes overview, tasks, markers, and blob link", () => {
    const plan = parsePlanFrontmatter(sampleFrontmatter);
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
