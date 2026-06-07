import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import { startReadAuxServer, type ReadAuxServer } from "./read-server.ts";
import {
  HttpReadBackend,
  type ReadObjectBackend,
  type ReadObjectResult,
} from "./read-object.ts";
import type { AdtObjectReference } from "./types.ts";

const fakeBackend: ReadObjectBackend = {
  async readObject(input): Promise<ReadObjectResult> {
    const objectName = input.objectName ?? "FROM_URI";
    return {
      kind: "source",
      reference: { name: objectName, type: "CLAS/OC", uri: "/u/x" },
      content: `SOURCE OF ${objectName}`,
      unsupported: false,
    };
  },
  async search(input): Promise<AdtObjectReference[]> {
    return [{ name: input.pattern, type: "CLAS/OC", uri: "/u/x" }];
  },
};

describe("read-server + HttpReadBackend round-trip", () => {
  let server: ReadAuxServer;

  beforeAll(async () => {
    server = await startReadAuxServer(fakeBackend, { token: "test-token" });
  });
  afterAll(() => server.stop());

  test("readObject forwards over HTTP and returns source", async () => {
    const client = new HttpReadBackend(server.url, "test-token");
    const result = await client.readObject({
      destination: "A4H",
      objectName: "CL_X",
    });
    expect(result.kind).toBe("source");
    if (result.kind === "source") {
      expect(result.content).toBe("SOURCE OF CL_X");
    }
  });

  test("search forwards over HTTP and returns references", async () => {
    const client = new HttpReadBackend(server.url, "test-token");
    const refs = await client.search({ destination: "A4H", pattern: "CL_*" });
    expect(refs).toHaveLength(1);
    expect(refs[0]!.type).toBe("CLAS/OC");
  });

  test("wrong bearer token is rejected", async () => {
    const client = new HttpReadBackend(server.url, "wrong");
    await expect(
      client.readObject({ destination: "A4H", objectName: "CL_X" }),
    ).rejects.toThrow();
  });
});
