import { describe, expect, test } from "bun:test";
import {
  augmentInstructions,
  getGuidancePrompt,
  guidancePromptDefs,
  isGuidancePrompt,
  GUIDANCE_INSTRUCTIONS,
} from "./guidance.ts";

describe("guidance", () => {
  test("guidancePromptDefs lists named prompts with arguments", () => {
    const defs = guidancePromptDefs();
    expect(defs.length).toBeGreaterThan(0);
    const names = defs.map((d) => d.name);
    expect(names).toContain("create-abap-object");
    for (const def of defs) {
      expect(def.name).toBeTruthy();
      expect(def.title).toBeTruthy();
      expect(def.description).toBeTruthy();
    }
  });

  test("isGuidancePrompt recognises only our prompts", () => {
    expect(isGuidancePrompt("create-abap-object")).toBe(true);
    expect(isGuidancePrompt("abap_list_destinations")).toBe(false);
    expect(isGuidancePrompt("")).toBe(false);
  });

  test("getGuidancePrompt substitutes arguments into the message", () => {
    const result = getGuidancePrompt("create-abap-object", {
      destination: "ABC_000_USER_EN",
      objectType: "CLAS",
    });
    expect(result).toBeDefined();
    const text = result!.messages[0]!.content.text;
    expect(text).toContain("ABC_000_USER_EN");
    expect(text).toContain("CLAS");
    expect(result!.messages[0]!.role).toBe("user");
  });

  test("getGuidancePrompt uses placeholders when args are missing", () => {
    const text =
      getGuidancePrompt("create-abap-object")!.messages[0]!.content.text;
    expect(text).toContain("<destination id>");
  });

  test("getGuidancePrompt returns undefined for unknown prompt", () => {
    expect(getGuidancePrompt("nope")).toBeUndefined();
  });

  test("augmentInstructions appends the guide to backend text", () => {
    const out = augmentInstructions("MCP server for ABAP Development");
    expect(out).toContain("MCP server for ABAP Development");
    expect(out).toContain(GUIDANCE_INSTRUCTIONS);
  });

  test("augmentInstructions handles empty/undefined backend text", () => {
    expect(augmentInstructions(undefined)).toBe(GUIDANCE_INSTRUCTIONS);
    expect(augmentInstructions("   ")).toBe(GUIDANCE_INSTRUCTIONS);
  });
});
