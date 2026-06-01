import AdmZip from "adm-zip";
import { spawnSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  readdirSync,
  rmSync,
  writeFileSync,
  copyFileSync,
} from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { homedir } from "node:os";
import { createInterface } from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";
import { fileURLToPath } from "node:url";
import * as tar from "tar";

const OPENADT_IMAGE_SDK_ROOT =
  process.env.OPENADT_SDK_ROOT?.trim() || "/opt/openadt";

const P2_ADT_REPOSITORY = "https://tools.hana.ondemand.com/latest";
const P2_PLUGIN_FILTER =
  "com.sap.adt.*,com.sap.conn.jco.*,org.eclipse.core.*,org.eclipse.equinox.*,org.eclipse.osgi*,org.osgi.*";

const REQUIRED_ADT_BUNDLE_PREFIXES = [
  "com.sap.adt.communication_",
  "com.sap.adt.destinations_",
  "com.sap.adt.destinations.model_",
  "com.sap.adt.compatibility_",
  "com.sap.adt.logging_",
  "com.sap.adt.util_",
] as const;

type Args = {
  sourceRoot?: string;
  jcoArchive?: string;
  cryptoArchive?: string;
  sapcarPath?: string;
  containerWorkspace?: string;
  nonInteractive: boolean;
  skipIfMissing: boolean;
  provisionP2Sdk: boolean;
  skipP2Sdk: boolean;
};

type DevcontainerRuntimePaths = {
  adtPluginsDir?: string;
  jcoJar: string;
  jcoNativeDir: string;
  sapcrypto?: string;
};

type StagedRuntime = {
  stageRoot: string;
  jcoJar: string;
  jcoNativeDir: string;
  sncDir: string;
  sapcrypto: string;
  sapgenpse: string;
  secudir: string;
  metadataDir: string;
};

type ExtractedCrypto = {
  root: string;
  sapcrypto: string;
  sapgenpse: string;
};

function parseArgs(argv: string[]): Args {
  const args: Args = {
    nonInteractive: false,
    skipIfMissing: false,
    provisionP2Sdk: false,
    skipP2Sdk: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case "--source-root":
        args.sourceRoot = argv[++i];
        break;
      case "--jco-archive":
        args.jcoArchive = argv[++i];
        break;
      case "--crypto-archive":
        args.cryptoArchive = argv[++i];
        break;
      case "--sapcar":
        args.sapcarPath = argv[++i];
        break;
      case "--container-workspace":
        args.containerWorkspace = argv[++i];
        break;
      case "--non-interactive":
        args.nonInteractive = true;
        break;
      case "--skip-if-missing":
        args.skipIfMissing = true;
        break;
      case "--provision-p2-sdk":
        args.provisionP2Sdk = true;
        break;
      case "--skip-p2-sdk":
        args.skipP2Sdk = true;
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return args;
}

function repoRoot(): string {
  return resolve(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
}

function repoTempRoot(root: string): string {
  return join(root, "tmp", "devcontainer-bootstrap");
}

function defaultSourceRoot(): string {
  const home = homedir();
  const userName = basename(home);
  const candidates = [
    join(home, ".openadt", "dist"),
    join("/mnt/c/Users", userName, ".openadt", "dist"),
  ];

  const userProfile = process.env.USERPROFILE;
  if (userProfile) {
    candidates.push(join(userProfile, ".openadt", "dist"));
  }

  return candidates.find((candidate) => existsSync(candidate)) ?? candidates[0];
}

function resolveContainerWorkspace(root: string, value?: string): string {
  if (value) {
    return value;
  }
  return `/workspaces/${root.split(/[\\/]/).at(-1)}`;
}

function walkFiles(root: string): string[] {
  if (!existsSync(root)) {
    return [];
  }
  const results: string[] = [];
  const stack = [root];
  while (stack.length > 0) {
    const current = stack.pop()!;
    for (const entry of readdirSync(current, { withFileTypes: true })) {
      const full = join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
      } else if (entry.isFile()) {
        results.push(full);
      }
    }
  }
  return results;
}

function pickByPattern(root: string, pattern: RegExp): string | undefined {
  return walkFiles(root)
    .sort()
    .find((file) => pattern.test(file.split(/[\\/]/).at(-1) ?? ""));
}

async function promptValue(
  label: string,
  defaultValue?: string,
): Promise<string> {
  const rl = createInterface({ input, output });
  try {
    const rendered = defaultValue
      ? `${label} [${defaultValue}]: `
      : `${label}: `;
    const answer = (await rl.question(rendered)).trim();
    return answer || defaultValue || "";
  } finally {
    rl.close();
  }
}

async function resolveInputFile(
  explicitPath: string | undefined,
  sourceRoot: string,
  pattern: RegExp,
  label: string,
  nonInteractive: boolean,
): Promise<string> {
  if (explicitPath) {
    const resolved = resolve(explicitPath);
    if (!existsSync(resolved)) {
      throw new Error(`${label} not found: ${resolved}`);
    }
    return resolved;
  }

  const candidate = pickByPattern(sourceRoot, pattern);
  if (candidate) {
    return candidate;
  }

  if (nonInteractive) {
    throw new Error(
      `${label} matching ${pattern} not found under ${sourceRoot}`,
    );
  }

  const entered = await promptValue(`Enter path to ${label}`);
  const resolved = resolve(entered);
  if (!existsSync(resolved)) {
    throw new Error(`${label} not found: ${resolved}`);
  }
  return resolved;
}

function recreateDir(path: string): void {
  rmSync(path, { recursive: true, force: true });
  mkdirSync(path, { recursive: true });
}

function bashQuote(value: string): string {
  return `'${value.replace(/'/g, `'\"'\"'`)}'`;
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function toWindowsPath(path: string): string {
  if (process.platform !== "linux" || !path.startsWith("/mnt/")) {
    return path;
  }

  const result = spawnSync("wslpath", ["-w", path], {
    stdio: "pipe",
    encoding: "utf8",
  });
  if (result.status !== 0) {
    throw new Error(
      `wslpath failed for ${path}\n${result.stderr || result.stdout}`,
    );
  }
  return result.stdout.trim();
}

async function expandJcoArchive(
  archivePath: string,
  tempRoot: string,
): Promise<{ jar: string; native: string }> {
  const zipExtract = join(tempRoot, "jco-zip");
  const tgzExtract = join(tempRoot, "jco-tgz");
  recreateDir(zipExtract);
  recreateDir(tgzExtract);

  new AdmZip(archivePath).extractAllTo(zipExtract, true);
  const tgz = walkFiles(zipExtract).find((file) => file.endsWith(".tgz"));
  if (!tgz) {
    throw new Error(`No .tgz payload found inside JCo archive: ${archivePath}`);
  }

  await tar.x({ file: tgz, cwd: tgzExtract });

  const jar = walkFiles(tgzExtract).find((file) =>
    file.endsWith("sapjco3.jar"),
  );
  const native = walkFiles(tgzExtract).find((file) =>
    file.endsWith("libsapjco3.so"),
  );
  if (!jar) {
    throw new Error(`sapjco3.jar not found after extracting ${archivePath}`);
  }
  if (!native) {
    throw new Error(`libsapjco3.so not found after extracting ${archivePath}`);
  }
  return { jar, native };
}

async function waitForExtractedFile(
  root: string,
  predicate: (file: string) => boolean,
): Promise<string | undefined> {
  for (let attempt = 0; attempt < 10; attempt++) {
    const match = walkFiles(root).find(predicate);
    if (match) {
      return match;
    }
    await delay(200);
  }
  return undefined;
}

async function expandCryptoArchive(
  archivePath: string,
  sapcarPath: string,
  tempRoot: string,
): Promise<ExtractedCrypto> {
  const cryptoExtract = join(tempRoot, "crypto");
  recreateDir(cryptoExtract);

  const result =
    process.platform === "linux" && /\.exe$/i.test(sapcarPath)
      ? spawnSync(
          "/bin/bash",
          [
            "-lc",
            `powershell.exe -NoProfile -Command ${bashQuote(
              `& '${toWindowsPath(sapcarPath).replace(/'/g, "''")}' -xvf '${toWindowsPath(archivePath).replace(/'/g, "''")}' -R '${toWindowsPath(cryptoExtract).replace(/'/g, "''")}'`,
            )}`,
          ],
          {
            stdio: "pipe",
            encoding: "utf8",
          },
        )
      : spawnSync(sapcarPath, ["-xvf", archivePath, "-R", cryptoExtract], {
          stdio: "pipe",
          encoding: "utf8",
        });

  if (result.status !== 0) {
    throw new Error(
      `SAPCAR failed to extract ${archivePath}\n${result.stderr || result.stdout}`,
    );
  }

  const sapcrypto = await waitForExtractedFile(cryptoExtract, (file) =>
    file.endsWith("libsapcrypto.so"),
  );
  const sapgenpse = await waitForExtractedFile(
    cryptoExtract,
    (file) => file.split(/[\\/]/).at(-1) === "sapgenpse",
  );
  if (!sapcrypto) {
    throw new Error(
      `libsapcrypto.so not found after extracting ${archivePath}`,
    );
  }
  if (!sapgenpse) {
    throw new Error(`sapgenpse not found after extracting ${archivePath}`);
  }
  return {
    root: cryptoExtract,
    sapcrypto,
    sapgenpse,
  };
}

function stageRuntime(
  root: string,
  jco: { jar: string; native: string },
  crypto: ExtractedCrypto,
): StagedRuntime {
  const stageRoot = join(root, ".devcontainer", "dist");
  const jcoStage = join(stageRoot, "jco");
  const sncStage = join(stageRoot, "snc");
  const metadataDir = join(stageRoot, "metadata");
  const secudir = join(root, ".devcontainer", "sec");

  recreateDir(jcoStage);
  recreateDir(sncStage);
  recreateDir(metadataDir);
  mkdirSync(secudir, { recursive: true });

  const jcoJar = join(jcoStage, "sapjco3.jar");
  const native = join(jcoStage, "libsapjco3.so");
  const sapcrypto = join(sncStage, "libsapcrypto.so");
  const sapgenpse = join(sncStage, "sapgenpse");

  copyFileSync(jco.jar, jcoJar);
  copyFileSync(jco.native, native);
  for (const file of walkFiles(crypto.root).sort()) {
    copyFileSync(file, join(sncStage, basename(file)));
  }

  return {
    stageRoot,
    jcoJar,
    jcoNativeDir: jcoStage,
    sncDir: sncStage,
    sapcrypto,
    sapgenpse,
    secudir,
    metadataDir,
  };
}

function detectStagedRuntime(root: string): StagedRuntime | undefined {
  const stageRoot = join(root, ".devcontainer", "dist");
  const metadataDir = join(stageRoot, "metadata");
  const jcoNativeDir = join(stageRoot, "jco");
  const sncDir = join(stageRoot, "snc");
  const secudir = join(root, ".devcontainer", "sec");
  const jcoJar = join(jcoNativeDir, "sapjco3.jar");
  const sapcrypto = join(sncDir, "libsapcrypto.so");
  const sapgenpse = join(sncDir, "sapgenpse");
  const cryptoKernel = join(sncDir, "libslcryptokernel.so");

  if (
    !existsSync(jcoJar) ||
    !existsSync(join(jcoNativeDir, "libsapjco3.so")) ||
    !existsSync(sapcrypto) ||
    !existsSync(sapgenpse) ||
    !existsSync(cryptoKernel)
  ) {
    return undefined;
  }

  mkdirSync(secudir, { recursive: true });

  return {
    stageRoot,
    jcoJar,
    jcoNativeDir,
    sncDir,
    sapcrypto,
    sapgenpse,
    secudir,
    metadataDir,
  };
}

function writeJson(path: string, value: unknown): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, JSON.stringify(value, null, 2));
}

function homeDestinationsDir(): string {
  return join(homedir(), ".openadt", "destinations");
}

function ensureProjectDestinations(root: string): void {
  const sourceDir = homeDestinationsDir();
  if (!existsSync(sourceDir)) {
    return;
  }

  const targetDir = join(root, ".openadt", "destinations");
  mkdirSync(targetDir, { recursive: true });

  for (const file of walkFiles(targetDir)) {
    if (
      file.split(/[\\/]/).at(-1)?.startsWith("generated-") &&
      file.endsWith(".openadt.toml")
    ) {
      rmSync(file, { force: true });
    }
  }

  for (const file of walkFiles(sourceDir).sort()) {
    const name = file.split(/[\\/]/).at(-1);
    if (!name?.endsWith(".openadt.toml")) {
      continue;
    }
    copyFileSync(file, join(targetDir, `generated-${name}`));
  }
}

function p2PluginsDir(root: string): string {
  return join(root, ".devcontainer/dist/p2/plugins");
}

function imageSdkPluginsDir(): string {
  return join(OPENADT_IMAGE_SDK_ROOT, "dist/p2/plugins");
}

function imageSdkJcoDir(): string {
  return join(OPENADT_IMAGE_SDK_ROOT, "dist/jco");
}

function imageSdkReady(): boolean {
  const pluginsDir = imageSdkPluginsDir();
  const native = join(imageSdkJcoDir(), "libsapjco3.so");
  return hasRequiredAdtBundles(pluginsDir) && existsSync(native);
}

function p2CliEntry(root: string): string {
  return join(root, "node_modules/@abapify/p2-cli/dist/cli.mjs");
}

function hasBundle(pluginsDir: string, prefix: string): boolean {
  if (!existsSync(pluginsDir)) {
    return false;
  }
  return readdirSync(pluginsDir).some(
    (name) => name.startsWith(prefix) && name.endsWith(".jar"),
  );
}

function hasRequiredAdtBundles(pluginsDir: string): boolean {
  return REQUIRED_ADT_BUNDLE_PREFIXES.every((prefix) =>
    hasBundle(pluginsDir, prefix),
  );
}

function findLatestJcoPluginJar(pluginsDir: string): string | undefined {
  if (!existsSync(pluginsDir)) {
    return undefined;
  }
  const pattern = /^com\.sap\.conn\.jco_(\d+(?:\.\d+)+)\.jar$/;
  let latest: { path: string; key: number[] } | undefined;
  for (const name of readdirSync(pluginsDir)) {
    const match = pattern.exec(name);
    if (!match) {
      continue;
    }
    const key = match[1].split(".").map((part) => Number.parseInt(part, 10));
    if (!latest || compareVersionKeys(key, latest.key) > 0) {
      latest = { path: join(pluginsDir, name), key };
    }
  }
  return latest?.path;
}

function compareVersionKeys(left: number[], right: number[]): number {
  const max = Math.max(left.length, right.length);
  for (let i = 0; i < max; i++) {
    const l = i < left.length ? left[i] : 0;
    const r = i < right.length ? right[i] : 0;
    const cmp = l - r;
    if (cmp !== 0) {
      return cmp;
    }
  }
  return 0;
}

function findLinuxJcoFragmentJar(pluginsDir: string): string | undefined {
  const files = walkFiles(pluginsDir).filter((file) =>
    /^com\.sap\.conn\.jco\.linux\.[^/\\]+\.jar$/i.test(basename(file)),
  );
  if (files.length === 0) {
    return undefined;
  }
  return files
    .map((file) => {
      const m = /\.(\d+(?:\.\d+)+)\.jar$/i.exec(basename(file));
      const key = m
        ? m[1].split(".").map((p) => Number.parseInt(p, 10))
        : [0];
      return { file, key };
    })
    .reduce((best, cur) =>
      compareVersionKeys(cur.key, best.key) > 0 ? cur : best,
    ).file;
}

function stageJcoNativeFromFragment(
  fragmentJar: string,
  jcoStageDir: string,
): void {
  mkdirSync(jcoStageDir, { recursive: true });
  const zip = new AdmZip(fragmentJar);
  const nativeEntry = zip
    .getEntries()
    .find(
      (entry) =>
        !entry.isDirectory && entry.entryName.endsWith("libsapjco3.so"),
    );
  if (!nativeEntry) {
    throw new Error(
      `libsapjco3.so not found inside Linux JCo fragment: ${fragmentJar}`,
    );
  }
  const target = join(jcoStageDir, "libsapjco3.so");
  writeFileSync(target, nativeEntry.getData());
}

function pathForChildProcess(): string {
  const current = process.env.PATH ?? process.env.Path ?? "";
  const delimiter = process.platform === "win32" ? ";" : ":";
  const segments = current.split(delimiter).filter((entry) => entry.length > 0);
  const seen = new Set(segments);
  const extras =
    process.platform === "win32"
      ? []
      : [
          "/usr/local/sbin",
          "/usr/local/bin",
          "/usr/sbin",
          "/usr/bin",
          "/sbin",
          "/bin",
        ];
  for (const extra of extras) {
    if (!seen.has(extra)) {
      segments.push(extra);
      seen.add(extra);
    }
  }
  return segments.join(delimiter);
}

function runP2Download(root: string, outputDir: string): void {
  const cli = p2CliEntry(root);
  if (!existsSync(cli)) {
    throw new Error(
      "Missing @abapify/p2-cli. Run `bun install` in the repository root first.",
    );
  }
  mkdirSync(outputDir, { recursive: true });
  const childEnv = { ...process.env, PATH: pathForChildProcess() };
  const result = spawnSync(
    process.execPath,
    [
      cli,
      "download",
      P2_ADT_REPOSITORY,
      "-o",
      outputDir,
      "-f",
      P2_PLUGIN_FILTER,
    ],
    { cwd: root, stdio: "inherit", env: childEnv },
  );
  if (result.status !== 0) {
    throw new Error(
      `p2-cli download failed (exit ${result.status ?? "unknown"})`,
    );
  }
}

async function ensureP2Sdk(root: string, args: Args): Promise<void> {
  if (args.skipP2Sdk) {
    return;
  }
  if (imageSdkReady()) {
    console.log(
      `Using SAP ADT SDK from container image (${OPENADT_IMAGE_SDK_ROOT})`,
    );
    return;
  }
  if (!args.provisionP2Sdk) {
    return;
  }

  const pluginsDir = p2PluginsDir(root);
  const jcoStageDir = join(root, ".devcontainer/dist/jco");
  const nativePath = join(jcoStageDir, "libsapjco3.so");
  if (hasRequiredAdtBundles(pluginsDir) && existsSync(nativePath)) {
    console.log(`Reusing staged ADT p2 plugins under ${pluginsDir}`);
    return;
  }

  console.log(`Downloading SAP ADT p2 plugins into ${pluginsDir}...`);
  runP2Download(root, join(root, ".devcontainer/dist/p2"));

  if (!hasRequiredAdtBundles(pluginsDir)) {
    throw new Error(
      `ADT p2 download finished but required bundles are missing under ${pluginsDir}`,
    );
  }

  const fragment = findLinuxJcoFragmentJar(pluginsDir);
  if (!fragment) {
    throw new Error(
      `No com.sap.conn.jco.linux.* fragment found under ${pluginsDir}`,
    );
  }
  stageJcoNativeFromFragment(fragment, jcoStageDir);
  console.log(`Staged libsapjco3.so from ${basename(fragment)}`);
}

function toContainerWorkspacePath(
  root: string,
  containerWorkspace: string,
  absolutePath: string,
): string {
  const repoRoot = resolve(root);
  const target = resolve(absolutePath);
  if (target.startsWith(repoRoot)) {
    const relative = target.slice(repoRoot.length).replace(/^[/\\]/, "");
    return join(containerWorkspace, relative).replaceAll("\\", "/");
  }
  return target.replaceAll("\\", "/");
}

function resolveWorkspaceSapcrypto(
  root: string,
  containerWorkspace: string,
  staged?: StagedRuntime,
): string | undefined {
  const cryptoPath = join(root, ".devcontainer/dist/snc/libsapcrypto.so");
  if (staged?.sapcrypto && existsSync(staged.sapcrypto)) {
    return `${containerWorkspace}/.devcontainer/dist/snc/libsapcrypto.so`;
  }
  if (existsSync(cryptoPath)) {
    return `${containerWorkspace}/.devcontainer/dist/snc/libsapcrypto.so`;
  }
  return undefined;
}

function resolveDevcontainerRuntime(
  root: string,
  containerWorkspace: string,
  staged?: StagedRuntime,
): DevcontainerRuntimePaths {
  const sapcrypto = resolveWorkspaceSapcrypto(root, containerWorkspace, staged);

  if (imageSdkReady()) {
    const pluginsDir = imageSdkPluginsDir();
    const jcoNativeDir = `${OPENADT_IMAGE_SDK_ROOT}/dist/jco`;
    const latestJco = existsSync(pluginsDir)
      ? findLatestJcoPluginJar(pluginsDir)
      : undefined;
    return {
      adtPluginsDir: `${OPENADT_IMAGE_SDK_ROOT}/dist/p2/plugins`,
      jcoJar: latestJco
        ? latestJco.replaceAll("\\", "/")
        : `${jcoNativeDir}/sapjco3.jar`,
      jcoNativeDir,
      sapcrypto,
    };
  }

  const pluginsDir = p2PluginsDir(root);
  const jcoNativeDir = `${containerWorkspace}/.devcontainer/dist/jco`;
  const latestJco = existsSync(pluginsDir)
    ? findLatestJcoPluginJar(pluginsDir)
    : undefined;
  const adtPluginsDir = hasRequiredAdtBundles(pluginsDir)
    ? `${containerWorkspace}/.devcontainer/dist/p2/plugins`
    : undefined;

  return {
    adtPluginsDir,
    jcoJar: latestJco
      ? toContainerWorkspacePath(root, containerWorkspace, latestJco)
      : `${containerWorkspace}/.devcontainer/dist/jco/sapjco3.jar`,
    jcoNativeDir,
    sapcrypto,
  };
}

function writeDevcontainerRuntime(
  path: string,
  runtime: DevcontainerRuntimePaths,
): void {
  mkdirSync(dirname(path), { recursive: true });
  const lines = [
    "version = 1",
    "",
    "[runtime]",
  ];
  if (runtime.adtPluginsDir) {
    lines.push(`adt_plugins_dir = "${runtime.adtPluginsDir}"`);
  }
  lines.push(`jco_jar = "${runtime.jcoJar}"`);
  lines.push(`jco_native_dir = "${runtime.jcoNativeDir}"`);
  if (runtime.sapcrypto) {
    lines.push(`sapcrypto = "${runtime.sapcrypto}"`);
  }
  lines.push("");
  writeFileSync(path, lines.join("\n"));
}

async function finalizeDevcontainer(
  root: string,
  containerWorkspace: string,
  args: Args,
  staged?: StagedRuntime,
): Promise<void> {
  await ensureP2Sdk(root, args);
  const devcontainerConfigPath = join(
    root,
    ".devcontainer",
    "openadt-config.toml",
  );
  const devcontainerRuntimePath = join(
    root,
    ".devcontainer",
    "runtime.openadt.toml",
  );
  ensureProjectDestinations(root);
  writeDevcontainerRuntime(
    devcontainerRuntimePath,
    resolveDevcontainerRuntime(root, containerWorkspace, staged),
  );
  writeDevcontainerEntrypoint(devcontainerConfigPath);
  console.log(
    `Updated devcontainer config entrypoint at ${devcontainerConfigPath}`,
  );
}

function writeDevcontainerEntrypoint(path: string): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(
    path,
    [
      "version = 1",
      "",
      "[merge]",
      'strategy = "last-wins"',
      "includes = [",
      '  "../.openadt/destinations/*.openadt.toml",',
      '  "runtime.openadt.toml"',
      "]",
      "",
    ].join("\n"),
  );
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  const root = repoRoot();
  const sourceRoot = resolve(args.sourceRoot ?? defaultSourceRoot());
  const containerWorkspace = resolveContainerWorkspace(
    root,
    args.containerWorkspace,
  );
  const staged = detectStagedRuntime(root);

  if (staged) {
    console.log(`Reused staged Linux SAP runtime under ${staged.stageRoot}`);
    await finalizeDevcontainer(root, containerWorkspace, args, staged);
    return;
  }

  if (!existsSync(sourceRoot)) {
    if (args.skipIfMissing) {
      console.log(
        `SAP archives not found under ${sourceRoot} — skipping native archive bootstrap.`,
      );
      await finalizeDevcontainer(root, containerWorkspace, args);
      return;
    }
    if (args.nonInteractive) {
      throw new Error(`Source root not found: ${sourceRoot}`);
    }
    const entered = await promptValue(
      "Enter source folder with SAP archives",
      sourceRoot,
    );
    if (!entered || !existsSync(resolve(entered))) {
      throw new Error(`Source root not found: ${entered}`);
    }
  }

  const effectiveSourceRoot = existsSync(sourceRoot)
    ? sourceRoot
    : resolve(
        await promptValue("Enter source folder with SAP archives", sourceRoot),
      );
  const jcoArchive = await resolveInputFile(
    args.jcoArchive,
    effectiveSourceRoot,
    /^sapjco31.*\.zip$/i,
    "Linux JCo archive",
    args.nonInteractive,
  );
  const cryptoArchive = await resolveInputFile(
    args.cryptoArchive,
    effectiveSourceRoot,
    /^SAPCRYPTOLIB.*\.SAR$/i,
    "Linux CryptoLib SAR archive",
    args.nonInteractive,
  );
  const sapcar = await resolveInputFile(
    args.sapcarPath,
    effectiveSourceRoot,
    /^SAPCAR.*\.(EXE|exe)$/i,
    "SAPCAR executable",
    args.nonInteractive,
  );

  const tempRoot = repoTempRoot(root);
  recreateDir(tempRoot);
  try {
    const jcoFiles = await expandJcoArchive(jcoArchive, tempRoot);
    const cryptoFiles = await expandCryptoArchive(
      cryptoArchive,
      sapcar,
      tempRoot,
    );
    const stage = stageRuntime(root, jcoFiles, cryptoFiles);

    writeJson(join(stage.metadataDir, "manifest.json"), {
      generated_at_utc: new Date().toISOString(),
      container_workspace: containerWorkspace,
      source: {
        jco_archive: jcoArchive,
        crypto_archive: cryptoArchive,
        sapcar,
      },
      staged: {
        jco_jar: stage.jcoJar,
        jco_native_dir: stage.jcoNativeDir,
        snc_dir: stage.sncDir,
        sapcrypto: stage.sapcrypto,
        sapgenpse: stage.sapgenpse,
        secudir: stage.secudir,
      },
    });

    console.log(`Staged Linux SAP runtime under ${stage.stageRoot}`);
    console.log(
      `Manifest written to ${join(stage.metadataDir, "manifest.json")}`,
    );
    await finalizeDevcontainer(root, containerWorkspace, args, stage);
  } finally {
    rmSync(tempRoot, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
