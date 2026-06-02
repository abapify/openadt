import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

/** Escape one entry for a Java `@argfile` (paths may contain spaces on Windows). */
export function escapeArgFileEntry(arg: string): string {
  if (!/[\s"]/.test(arg)) {
    return arg;
  }
  return `"${arg.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

/**
 * Run Java with a classpath that may exceed Windows command-line limits (Eclipse p2).
 */
export function spawnJavaWithClasspath(options: {
  classpath: string;
  mainClass: string;
  args: string[];
  cwd?: string;
  env?: NodeJS.ProcessEnv;
}): number {
  const javaArgs = [
    "-cp",
    options.classpath,
    options.mainClass,
    ...options.args,
  ];
  const useArgFile = process.platform === "win32";
  if (!useArgFile) {
    const result = spawnSync("java", javaArgs, {
      stdio: "inherit",
      cwd: options.cwd,
      env: options.env ?? process.env,
    });
    return result.status ?? 1;
  }
  const dir = mkdtempSync(join(tmpdir(), "openadt-java-"));
  const argFile = join(dir, "args.txt");
  try {
    writeFileSync(argFile, javaArgs.map(escapeArgFileEntry).join("\n"), "utf8");
    const result = spawnSync("java", [`@${argFile}`], {
      stdio: "inherit",
      cwd: options.cwd,
      env: options.env ?? process.env,
    });
    return result.status ?? 1;
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}
