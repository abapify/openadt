import { describe, expect, test } from "bun:test";
import {
  handleReadToolCall,
  isReadTool,
  isUnsupportedPlaceholder,
  LspReadBackend,
  pickReference,
  readToolDefs,
  ReadTimeoutError,
  retryUntilNonEmpty,
  type LspRequester,
} from "./read-object.ts";
import type { AdtObjectReference } from "./types.ts";

const ref = (name: string, type: string, uri: string): AdtObjectReference => ({
  name,
  type,
  uri,
});

describe("read-object: tool surface", () => {
  test("readToolDefs lists both tools with input schemas", () => {
    const defs = readToolDefs() as { name: string; inputSchema: unknown }[];
    const names = defs.map((d) => d.name);
    expect(names).toContain("adt_read_object");
    expect(names).toContain("adt_search_objects");
    for (const def of defs) {
      expect(def.inputSchema).toBeTruthy();
    }
  });

  test("isReadTool recognises only our tools", () => {
    expect(isReadTool("adt_read_object")).toBe(true);
    expect(isReadTool("adt_search_objects")).toBe(true);
    expect(isReadTool("abap_list_destinations")).toBe(false);
  });

  test("isUnsupportedPlaceholder detects the adt-ls note", () => {
    expect(
      isUnsupportedPlaceholder(
        "// The object is not supported in ADT in VS Code.",
      ),
    ).toBe(true);
    expect(isUnsupportedPlaceholder("CLASS zcl_x DEFINITION.")).toBe(false);
  });
});

describe("read-object: pickReference", () => {
  const refs = [
    ref("CL_X", "CLAS/OC", "/sap/bc/adt/oo/classes/cl_x"),
    ref("CL_X", "INTF/OI", "/sap/bc/adt/oo/interfaces/cl_x"),
    ref("CL_XYZ", "CLAS/OC", "/sap/bc/adt/oo/classes/cl_xyz"),
  ];

  test("exact name + type → single match", () => {
    const got = pickReference(refs, { name: "cl_x", type: "CLAS/OC" });
    expect("match" in got && got.match.uri).toBe("/sap/bc/adt/oo/classes/cl_x");
  });

  test("ambiguous name without type → candidates", () => {
    const got = pickReference(refs, { name: "CL_X" });
    expect("candidates" in got && got.candidates.length).toBe(2);
  });

  test("single hit, name not exact → match", () => {
    const got = pickReference([ref("CL_XYZ", "CLAS/OC", "u")], {
      name: "CL_X",
    });
    expect("match" in got && got.match.name).toBe("CL_XYZ");
  });
});

describe("read-object: retryUntilNonEmpty (never return empty)", () => {
  test("returns once non-empty", async () => {
    let calls = 0;
    const out = await retryUntilNonEmpty(
      async () => {
        calls += 1;
        return calls < 2 ? [] : [1];
      },
      (v: number[]) => v.length === 0,
      "test",
      { timeoutMs: 1_000, intervalMs: 1 },
    );
    expect(out).toEqual([1]);
    expect(calls).toBe(2);
  });

  test("throws ReadTimeoutError instead of yielding empty", async () => {
    await expect(
      retryUntilNonEmpty(
        async () => [],
        (v: number[]) => v.length === 0,
        "test",
        { timeoutMs: 20, intervalMs: 5 },
      ),
    ).rejects.toBeInstanceOf(ReadTimeoutError);
  });
});

describe("read-object: LspReadBackend over a fake requester", () => {
  function fakeRequester(
    handlers: Record<string, (params: object) => unknown>,
  ): LspRequester {
    return (<T>(method: string, params: object): Promise<T> => {
      const h = handlers[method];
      if (!h) return Promise.reject(new Error(`unexpected ${method}`));
      return Promise.resolve(h(params) as T);
    }) as LspRequester;
  }

  test("readObject: quickSearch → getLsUri → readFile", async () => {
    const req = fakeRequester({
      "adtLs/repository/quickSearch": () => ({
        references: [ref("CL_X", "CLAS/OC", "/sap/bc/adt/oo/classes/cl_x")],
      }),
      "adtLs/repository/getLsUri": (p) => {
        expect((p as { adtUri: string }).adtUri).toBe(
          "/sap/bc/adt/oo/classes/cl_x",
        );
        return { uri: "abap:/repotree-v1/A4H/.../cl_x.clas.abap" };
      },
      "adtLs/fileSystem/readFile": () => ({
        content: "CLASS cl_x DEFINITION.",
      }),
    });
    const backend = new LspReadBackend(req, { timeoutMs: 500, intervalMs: 5 });
    const result = await backend.readObject({
      destination: "A4H",
      objectName: "CL_X",
    });
    expect(result.kind).toBe("source");
    if (result.kind === "source") {
      expect(result.content).toContain("CLASS cl_x");
      expect(result.unsupported).toBe(false);
    }
  });

  test("readObject: classic type placeholder is flagged unsupported", async () => {
    const req = fakeRequester({
      "adtLs/repository/quickSearch": () => ({
        references: [
          ref("Z_PROG", "PROG/P", "/sap/bc/adt/programs/programs/z_prog"),
        ],
      }),
      "adtLs/repository/getLsUri": () => ({
        uri: "abap:/repotree-v1/A4H/z_prog.prog.json",
      }),
      "adtLs/fileSystem/readFile": () => ({
        content: "// The object is not supported in ADT in VS Code.",
      }),
    });
    const backend = new LspReadBackend(req, { timeoutMs: 500, intervalMs: 5 });
    const result = await backend.readObject({
      destination: "A4H",
      objectName: "Z_PROG",
    });
    expect(result.kind === "source" && result.unsupported).toBe(true);
  });

  test("readObject: empty search → ReadTimeoutError (not empty result)", async () => {
    const req = fakeRequester({
      "adtLs/repository/quickSearch": () => ({ references: [] }),
    });
    const backend = new LspReadBackend(req, { timeoutMs: 20, intervalMs: 5 });
    await expect(
      backend.readObject({ destination: "A4H", objectName: "NOPE" }),
    ).rejects.toBeInstanceOf(ReadTimeoutError);
  });

  test("handleReadToolCall adt_read_object returns source text", async () => {
    const req = fakeRequester({
      "adtLs/repository/quickSearch": () => ({
        references: [ref("CL_X", "CLAS/OC", "/u/cl_x")],
      }),
      "adtLs/repository/getLsUri": () => ({
        uri: "abap:/repotree-v1/A4H/cl_x.clas.abap",
      }),
      "adtLs/fileSystem/readFile": () => ({ content: "CLASS cl_x." }),
    });
    const backend = new LspReadBackend(req, { timeoutMs: 500, intervalMs: 5 });
    const res = await handleReadToolCall(backend, {
      name: "adt_read_object",
      args: { destination: "A4H", objectName: "CL_X" },
    });
    expect(res.isError).toBeFalsy();
    expect(res.content[0]!.text).toContain("CLASS cl_x.");
  });

  test("readObject: by uri skips quickSearch (search→read composition)", async () => {
    let searched = false;
    const req = fakeRequester({
      "adtLs/repository/quickSearch": () => {
        searched = true;
        return { references: [] };
      },
      "adtLs/repository/getLsUri": (p) => {
        expect((p as { adtUri: string }).adtUri).toBe(
          "/sap/bc/adt/oo/classes/zcl_otel_span",
        );
        return { uri: "abap:/repotree-v1/A4H/zcl_otel_span.clas.abap" };
      },
      "adtLs/fileSystem/readFile": () => ({ content: "CLASS zcl_otel_span." }),
    });
    const backend = new LspReadBackend(req, { timeoutMs: 500, intervalMs: 5 });
    const result = await backend.readObject({
      destination: "A4H",
      uri: "/sap/bc/adt/oo/classes/zcl_otel_span",
    });
    expect(searched).toBe(false);
    expect(result.kind).toBe("source");
    if (result.kind === "source") {
      expect(result.reference.uri).toBe("/sap/bc/adt/oo/classes/zcl_otel_span");
      expect(result.reference.name).toBe("ZCL_OTEL_SPAN");
      expect(result.content).toContain("CLASS zcl_otel_span.");
    }
  });

  test("handleReadToolCall validates required args", async () => {
    const backend = new LspReadBackend(fakeRequester({}));
    const res = await handleReadToolCall(backend, {
      name: "adt_read_object",
      args: {},
    });
    expect(res.isError).toBe(true);
  });

  test("handleReadToolCall adt_search_objects returns JSON + structuredContent", async () => {
    const req = fakeRequester({
      "adtLs/repository/quickSearch": () => ({
        references: [
          {
            name: "ZCL_X",
            type: "Class",
            description: "demo",
            uri: "/u/zcl_x",
          },
        ],
      }),
    });
    const backend = new LspReadBackend(req, { timeoutMs: 500, intervalMs: 5 });
    const res = await handleReadToolCall(backend, {
      name: "adt_search_objects",
      args: { destination: "A4H", pattern: "ZCL_*" },
    });
    expect(res.isError).toBeFalsy();
    const parsed = JSON.parse(res.content[0]!.text) as {
      references: { name: string; description?: string }[];
    };
    expect(parsed.references[0]!.name).toBe("ZCL_X");
    expect(parsed.references[0]!.description).toBe("demo");
    const structured = res.structuredContent as {
      references: unknown[];
    };
    expect(structured.references).toHaveLength(1);
  });

  test("adt_search_objects honours format=markdown / compact", async () => {
    const req = fakeRequester({
      "adtLs/repository/quickSearch": () => ({
        references: [
          { name: "ZCL_X", type: "Class", description: "demo", uri: "/u" },
        ],
      }),
    });
    const backend = new LspReadBackend(req, { timeoutMs: 500, intervalMs: 5 });
    const md = await handleReadToolCall(backend, {
      name: "adt_search_objects",
      args: { destination: "A4H", pattern: "ZCL_*", format: "markdown" },
    });
    expect(md.content[0]!.text).toContain("| Name | Type | Description |");
    expect(md.content[0]!.text).toContain("| ZCL_X | Class | demo |");

    const compact = await handleReadToolCall(backend, {
      name: "adt_search_objects",
      args: { destination: "A4H", pattern: "ZCL_*", format: "compact" },
    });
    expect(compact.content[0]!.text).toBe("ZCL_X — demo (Class)");
    // structuredContent stays JSON regardless of text format
    expect(
      (compact.structuredContent as { references: unknown[] }).references,
    ).toHaveLength(1);
  });
});
