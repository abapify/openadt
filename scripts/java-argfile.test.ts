import { describe, expect, test } from "bun:test";
import { escapeArgFileEntry } from "./java-argfile.ts";

describe("escapeArgFileEntry", () => {
  test("leaves simple entries unchanged", () => {
    expect(escapeArgFileEntry("-cp")).toBe("-cp");
  });

  test("quotes entries with spaces", () => {
    expect(escapeArgFileEntry("C:\\Program Files\\java")).toBe(
      '"C:\\\\Program Files\\\\java"',
    );
  });
});
