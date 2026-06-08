import { describe, expect, test } from "bun:test";
import { parseBridgeArgv, parseServeArgv, parseStopArgv } from "./config.ts";

describe("config", () => {
  describe("parseServeArgv", () => {
    test("--stdio sets stdio=true", () => {
      const cfg = parseServeArgv(["--stdio"]);
      expect(cfg.stdio).toBe(true);
      expect(cfg.standalone).toBe(false);
    });

    test("--standalone sets standalone=true", () => {
      const cfg = parseServeArgv(["--stdio", "--standalone"]);
      expect(cfg.stdio).toBe(true);
      expect(cfg.standalone).toBe(true);
    });

    test("--port sets explicit port", () => {
      const cfg = parseServeArgv(["--port", "3000"]);
      expect(cfg.port).toBe(3000);
    });

    test("unknown argument throws", () => {
      expect(() => parseServeArgv(["--unknown"])).toThrow("Unknown argument");
    });

    test("invalid port throws", () => {
      expect(() => parseServeArgv(["--port", "70000"])).toThrow();
    });
  });

  describe("parseStopArgv", () => {
    test("parses --port flag", () => {
      const r = parseStopArgv(["--port", "2236"]);
      expect(r.port).toBe(2236);
      expect(r.json).toBe(false);
    });

    test("parses --json flag", () => {
      const r = parseStopArgv(["--json"]);
      expect(r.json).toBe(true);
      expect(r.port).toBeUndefined();
    });

    test("parses --port=2245 eq form", () => {
      const r = parseStopArgv(["--port=2245"]);
      expect(r.port).toBe(2245);
    });

    test("invalid port throws", () => {
      expect(() => parseStopArgv(["--port", "0"])).toThrow();
    });
  });

  describe("parseBridgeArgv", () => {
    test("requires --stdio", () => {
      const r = parseBridgeArgv([]);
      expect(r.stdio).toBe(false);
      expect(r.port).toBeUndefined();
      expect(r.json).toBe(false);
    });

    test("parses --stdio and --port", () => {
      const r = parseBridgeArgv(["--stdio", "--port", "2250"]);
      expect(r.stdio).toBe(true);
      expect(r.port).toBe(2250);
    });

    test("parses --json", () => {
      const r = parseBridgeArgv(["--stdio", "--json"]);
      expect(r.stdio).toBe(true);
      expect(r.json).toBe(true);
    });
  });
});
