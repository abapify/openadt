/**
 * Agent guidance injected by the launcher's stdio bridge.
 *
 * The SAP ADT MCP server advertises the `prompts` capability but returns an
 * empty `prompts/list`, and ships an almost-empty `initialize.instructions`
 * string. Since our bridge proxies every JSON-RPC message we can, without
 * touching the backend:
 *   1. append a compact workflow cheat-sheet to `initialize.instructions`
 *      (delivered to every client automatically — best lever for weak models);
 *   2. serve our own `prompts/list` + `prompts/get` (guided workflows for
 *      clients that surface MCP prompts).
 *
 * The TEXT lives in ./prompts/*.md (one file per prompt + instructions.md);
 * this module only loads those files and substitutes `{{placeholders}}`.
 * Edit the .md files to tune wording — no code change needed.
 *
 * Set OPENADT_MCP_NO_GUIDANCE=1 to disable injection entirely.
 *
 * See specs/mcp-shared-backend.md and specs/mcp.md.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { isTruthyEnv } from "./process.ts";

const PROMPTS_DIR = join(dirname(fileURLToPath(import.meta.url)), "prompts");

/** Read a prompt/instruction markdown file; empty string if missing. */
function loadText(file: string): string {
  try {
    return readFileSync(join(PROMPTS_DIR, file), "utf8").trim();
  } catch {
    return "";
  }
}

/** Replace `{{key}}` tokens; unknown keys fall back to `<key>`. */
function render(template: string, values: Record<string, string>): string {
  return template.replace(/\{\{(\w+)\}\}/g, (_, key: string) =>
    values[key] && values[key]!.trim() ? values[key]! : `<${key}>`,
  );
}

/** Whether guidance injection is enabled (default on). */
export function guidanceEnabled(): boolean {
  return !isTruthyEnv(process.env.OPENADT_MCP_NO_GUIDANCE);
}

/**
 * Workflow cheat-sheet appended to the backend's `initialize.instructions`.
 * Source: ./prompts/instructions.md
 */
export const GUIDANCE_INSTRUCTIONS = loadText("instructions.md");

/** One MCP prompt definition (for prompts/list). */
export type GuidancePrompt = {
  name: string;
  title: string;
  description: string;
  arguments?: { name: string; description: string; required?: boolean }[];
};

/** prompts/get result shape (subset of the MCP spec). */
export type PromptGetResult = {
  description: string;
  messages: {
    role: "user" | "assistant";
    content: { type: "text"; text: string };
  }[];
};

/**
 * Prompt registry. `def` is the prompts/list metadata, `file` is the markdown
 * body in ./prompts/, and `defaults` supplies friendly fallbacks for any
 * placeholder the caller leaves unset.
 */
const PROMPTS: {
  def: GuidancePrompt;
  file: string;
  defaults: Record<string, string>;
}[] = [
  {
    def: {
      name: "create-abap-object",
      title: "Create an ABAP object",
      description:
        "Guided workflow to create a new ABAP object (class, table, CDS view, …) on a destination, with transport handling and activation.",
      arguments: [
        {
          name: "destination",
          description: "Destination id, e.g. ABC_000_USER_EN",
        },
        { name: "objectType", description: "Object type to create (optional)" },
        { name: "name", description: "Object name (optional)" },
        { name: "package", description: "Target package (optional)" },
      ],
    },
    file: "create-abap-object.md",
    defaults: {
      destination: "<destination id>",
      objectType: "ask the user",
      name: "the object",
      package: "the target package",
    },
  },
  {
    def: {
      name: "generate-rap-service",
      title: "Generate a RAP service",
      description:
        "Guided workflow to generate RAP objects (tables, CDS, behavior, service definition/binding) with an ABAP generator.",
      arguments: [
        { name: "destination", description: "Destination id" },
        {
          name: "package",
          description: "Target package (required by generators)",
        },
        {
          name: "scenario",
          description:
            "What to generate, e.g. 'UI service over table' (optional)",
        },
      ],
    },
    file: "generate-rap-service.md",
    defaults: {
      destination: "<destination id>",
      package: "ask the user for a package",
      scenario: "the requested scenario",
    },
  },
  {
    def: {
      name: "expose-odata-service",
      title: "Inspect / expose an OData service",
      description:
        "Guided workflow to fetch OData service information from a service binding (needed before Fiori app generation).",
      arguments: [
        { name: "destination", description: "Destination id" },
        {
          name: "serviceBindingName",
          description: "Service binding name (optional)",
        },
      ],
    },
    file: "expose-odata-service.md",
    defaults: {
      destination: "<destination id>",
      serviceBindingName: "the service binding",
    },
  },
  {
    def: {
      name: "activate-and-test",
      title: "Activate and run unit tests",
      description: "Activate ABAP objects and run ABAP Unit tests for them.",
      arguments: [
        { name: "destination", description: "Destination id" },
        {
          name: "objects",
          description: "Object name(s) to activate/test (optional)",
        },
      ],
    },
    file: "activate-and-test.md",
    defaults: {
      destination: "<destination id>",
      objects: "the changed objects",
    },
  },
];

const PROMPT_BY_NAME = new Map(PROMPTS.map((p) => [p.def.name, p]));

/** Prompt definitions to merge into the backend's (empty) prompts/list. */
export function guidancePromptDefs(): GuidancePrompt[] {
  return PROMPTS.map((p) => p.def);
}

/** Whether `name` is one of our injected prompts. */
export function isGuidancePrompt(name: string): boolean {
  return PROMPT_BY_NAME.has(name);
}

/** Build a prompts/get result for one of our prompts, or undefined if unknown. */
export function getGuidancePrompt(
  name: string,
  args: Record<string, string> = {},
): PromptGetResult | undefined {
  const entry = PROMPT_BY_NAME.get(name);
  if (!entry) {
    return undefined;
  }
  const text = render(loadText(entry.file), { ...entry.defaults, ...args });
  return {
    description: entry.def.description,
    messages: [{ role: "user", content: { type: "text", text } }],
  };
}

/** Append the workflow cheat-sheet to a backend instructions string. */
export function augmentInstructions(backend: string | undefined): string {
  const base = (backend ?? "").trim();
  if (!GUIDANCE_INSTRUCTIONS) {
    return base;
  }
  return base ? `${base}\n\n${GUIDANCE_INSTRUCTIONS}` : GUIDANCE_INSTRUCTIONS;
}
