import { describe, expect, test } from "bun:test";
import { redactSecrets } from "./log.ts";

describe("redactSecrets", () => {
  test("redacts Bearer tokens", () => {
    const out = redactSecrets("Authorization: Bearer abc.def-123_~+/=");
    expect(out).toContain("Bearer [REDACTED]");
    expect(out).not.toContain("abc.def");
  });

  test("redacts JSON token and password fields", () => {
    const out = redactSecrets(
      '{"token":"secret","password":"p","clientSecret":"cs"}',
    );
    expect(out).toContain('"token":"[REDACTED]"');
    expect(out).toContain('"password":"[REDACTED]"');
    expect(out).not.toContain("secret");
  });
});
