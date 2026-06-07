import tseslint from "typescript-eslint";

/**
 * OpenADT ESLint config — CodeScene-matching complexity thresholds.
 *
 * Two tiers:
 *   - `scripts/**` and `.agents/skills/**`  →  ERROR (gates `bunx eslint . --max-warnings 0`)
 *   - everything else (e.g. `tools/sap-adt-mcp-launcher/**`)  →  WARN (visible, non-blocking)
 *
 * Why tiered: 27 pre-existing complexity violations exist in
 * `tools/sap-adt-mcp-launcher/**` on main (CC 10-29, max-lines 217).
 * CodeScene's delta gate only runs on changed files; ESLint's `lint .`
 * runs on everything. The strict tier covers the dirs with the highest
 * churn and the fewest pre-existing violations.
 *
 * The agent-facing rule is: new code in `scripts/**` must clear all
 * errors. In `tools/**` it can introduce complexity, but the warning
 * surfaces it for review.
 *
 * To check only new code on a branch:
 *   git diff --name-only origin/main..HEAD | xargs bunx eslint
 */
export default tseslint.config(
  {
    ignores: [
      "node_modules/**",
      ".nx/**",
      ".kilo/**",
      "target/**",
      "dist/**",
      "**/generated/**",
      "packaging/**",
      "homebrew-openadt/**",
      "scoop-bucket/**",
      "*.md",
      "*.json",
      "*.jsonc",
    ],
  },
  // ── Tier 1: STRICT (gates --max-warnings 0) ─────────────────────
  {
    files: [
      "scripts/**/*.{js,mjs,cjs,ts,tsx}",
      ".agents/skills/**/*.{js,mjs,cjs,ts,tsx}",
    ],
    rules: {
      complexity: ["error", { max: 9 }],
      "max-depth": ["error", 4],
      "max-params": ["error", 4],
      "max-lines-per-function": [
        "error",
        { max: 70, skipBlankLines: true, skipComments: true },
      ],
      "max-lines": [
        "error",
        { max: 1000, skipBlankLines: true, skipComments: true },
      ],
      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_" },
      ],
    },
  },
  // ── Tier 1 exceptions: known CC overflow on legacy scripts/ ────
  // Tracked for follow-up refactor. Add new exceptions here only with justification.
  {
    files: ["scripts/nx-openadt.ts", "scripts/sdk-classpath.ts"],
    rules: {
      complexity: "off", // firstSubcommandIndex=12, buildSdkClasspathEntries=10
    },
  },
  // ── Tier 2: WARN everywhere else (advisory, non-blocking) ───────
  ...tseslint.configs.recommended.map((config) => ({
    ...config,
    rules: {
      ...config.rules,
      complexity: ["warn", { max: 9 }],
      "max-depth": ["warn", 4],
      "max-params": ["warn", 4],
      "max-lines-per-function": [
        "warn",
        { max: 70, skipBlankLines: true, skipComments: true },
      ],
      "max-lines": [
        "warn",
        { max: 1000, skipBlankLines: true, skipComments: true },
      ],
      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_" },
      ],
    },
  })),
);
