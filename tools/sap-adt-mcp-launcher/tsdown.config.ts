import { defineConfig } from "tsdown";

export default defineConfig({
  entry: ["src/main.ts", "src/mcp-stdio-entry.ts"],
  format: "esm",
  platform: "node",
  target: "node20",
  outDir: "dist",
  clean: true,
  treeshake: true,
  shims: true,
  dts: false,
});
