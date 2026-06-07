import js from "@eslint/js";
import tseslint from "typescript-eslint";

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
  {
    files: ["**/*.{js,mjs,cjs}"],
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
    },
  },
  ...tseslint.configs.recommended.map((config) => ({
    ...config,
    files: ["**/*.{ts,tsx}"],
    rules: {
      ...config.rules,
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
  })),
);
