import AdmZip from 'adm-zip';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, rmSync, writeFileSync, copyFileSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { homedir } from 'node:os';
import { createInterface } from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';
import { fileURLToPath } from 'node:url';
import * as tar from 'tar';

type Args = {
  sourceRoot?: string;
  jcoArchive?: string;
  cryptoArchive?: string;
  sapcarPath?: string;
  containerWorkspace?: string;
  nonInteractive: boolean;
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
  const args: Args = { nonInteractive: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case '--source-root':
        args.sourceRoot = argv[++i];
        break;
      case '--jco-archive':
        args.jcoArchive = argv[++i];
        break;
      case '--crypto-archive':
        args.cryptoArchive = argv[++i];
        break;
      case '--sapcar':
        args.sapcarPath = argv[++i];
        break;
      case '--container-workspace':
        args.containerWorkspace = argv[++i];
        break;
      case '--non-interactive':
        args.nonInteractive = true;
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return args;
}

function repoRoot(): string {
  return resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
}

function repoTempRoot(root: string): string {
  return join(root, 'tmp', 'devcontainer-bootstrap');
}

function defaultSourceRoot(): string {
  const home = homedir();
  const userName = basename(home);
  const candidates = [
    join(home, '.openadt', 'dist'),
    join('/mnt/c/Users', userName, '.openadt', 'dist')
  ];

  const userProfile = process.env.USERPROFILE;
  if (userProfile) {
    candidates.push(join(userProfile, '.openadt', 'dist'));
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
  return walkFiles(root).sort().find((file) => pattern.test(file.split(/[\\/]/).at(-1) ?? ''));
}

async function promptValue(label: string, defaultValue?: string): Promise<string> {
  const rl = createInterface({ input, output });
  try {
    const rendered = defaultValue ? `${label} [${defaultValue}]: ` : `${label}: `;
    const answer = (await rl.question(rendered)).trim();
    return answer || defaultValue || '';
  } finally {
    rl.close();
  }
}

async function resolveInputFile(
  explicitPath: string | undefined,
  sourceRoot: string,
  pattern: RegExp,
  label: string,
  nonInteractive: boolean
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
    throw new Error(`${label} matching ${pattern} not found under ${sourceRoot}`);
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
  if (process.platform !== 'linux' || !path.startsWith('/mnt/')) {
    return path;
  }

  const result = spawnSync('wslpath', ['-w', path], {
    stdio: 'pipe',
    encoding: 'utf8'
  });
  if (result.status !== 0) {
    throw new Error(`wslpath failed for ${path}\n${result.stderr || result.stdout}`);
  }
  return result.stdout.trim();
}

async function expandJcoArchive(archivePath: string, tempRoot: string): Promise<{ jar: string; native: string }> {
  const zipExtract = join(tempRoot, 'jco-zip');
  const tgzExtract = join(tempRoot, 'jco-tgz');
  recreateDir(zipExtract);
  recreateDir(tgzExtract);

  new AdmZip(archivePath).extractAllTo(zipExtract, true);
  const tgz = walkFiles(zipExtract).find((file) => file.endsWith('.tgz'));
  if (!tgz) {
    throw new Error(`No .tgz payload found inside JCo archive: ${archivePath}`);
  }

  await tar.x({ file: tgz, cwd: tgzExtract });

  const jar = walkFiles(tgzExtract).find((file) => file.endsWith('sapjco3.jar'));
  const native = walkFiles(tgzExtract).find((file) => file.endsWith('libsapjco3.so'));
  if (!jar) {
    throw new Error(`sapjco3.jar not found after extracting ${archivePath}`);
  }
  if (!native) {
    throw new Error(`libsapjco3.so not found after extracting ${archivePath}`);
  }
  return { jar, native };
}

async function waitForExtractedFile(root: string, predicate: (file: string) => boolean): Promise<string | undefined> {
  for (let attempt = 0; attempt < 10; attempt++) {
    const match = walkFiles(root).find(predicate);
    if (match) {
      return match;
    }
    await delay(200);
  }
  return undefined;
}

async function expandCryptoArchive(archivePath: string, sapcarPath: string, tempRoot: string): Promise<ExtractedCrypto> {
  const cryptoExtract = join(tempRoot, 'crypto');
  recreateDir(cryptoExtract);

  const result =
    process.platform === 'linux' && /\.exe$/i.test(sapcarPath)
      ? spawnSync(
          '/bin/bash',
          [
            '-lc',
            `powershell.exe -NoProfile -Command ${bashQuote(
              `& '${toWindowsPath(sapcarPath).replace(/'/g, "''")}' -xvf '${toWindowsPath(archivePath).replace(/'/g, "''")}' -R '${toWindowsPath(cryptoExtract).replace(/'/g, "''")}'`
            )}`
          ],
          {
            stdio: 'pipe',
            encoding: 'utf8'
          }
        )
      : spawnSync(sapcarPath, ['-xvf', archivePath, '-R', cryptoExtract], {
          stdio: 'pipe',
          encoding: 'utf8'
        });

  if (result.status !== 0) {
    throw new Error(`SAPCAR failed to extract ${archivePath}\n${result.stderr || result.stdout}`);
  }

  const sapcrypto = await waitForExtractedFile(cryptoExtract, (file) => file.endsWith('libsapcrypto.so'));
  const sapgenpse = await waitForExtractedFile(cryptoExtract, (file) => file.split(/[\\/]/).at(-1) === 'sapgenpse');
  if (!sapcrypto) {
    throw new Error(`libsapcrypto.so not found after extracting ${archivePath}`);
  }
  if (!sapgenpse) {
    throw new Error(`sapgenpse not found after extracting ${archivePath}`);
  }
  return {
    root: cryptoExtract,
    sapcrypto,
    sapgenpse
  };
}

function stageRuntime(root: string, jco: { jar: string; native: string }, crypto: ExtractedCrypto): StagedRuntime {
  const stageRoot = join(root, '.devcontainer', 'dist');
  const jcoStage = join(stageRoot, 'jco');
  const sncStage = join(stageRoot, 'snc');
  const metadataDir = join(stageRoot, 'metadata');
  const secudir = join(root, '.devcontainer', 'sec');

  recreateDir(jcoStage);
  recreateDir(sncStage);
  recreateDir(metadataDir);
  mkdirSync(secudir, { recursive: true });

  const jcoJar = join(jcoStage, 'sapjco3.jar');
  const native = join(jcoStage, 'libsapjco3.so');
  const sapcrypto = join(sncStage, 'libsapcrypto.so');
  const sapgenpse = join(sncStage, 'sapgenpse');

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
    metadataDir
  };
}

function detectStagedRuntime(root: string): StagedRuntime | undefined {
  const stageRoot = join(root, '.devcontainer', 'dist');
  const metadataDir = join(stageRoot, 'metadata');
  const jcoNativeDir = join(stageRoot, 'jco');
  const sncDir = join(stageRoot, 'snc');
  const secudir = join(root, '.devcontainer', 'sec');
  const jcoJar = join(jcoNativeDir, 'sapjco3.jar');
  const sapcrypto = join(sncDir, 'libsapcrypto.so');
  const sapgenpse = join(sncDir, 'sapgenpse');
  const cryptoKernel = join(sncDir, 'libslcryptokernel.so');

  if (!existsSync(jcoJar)
    || !existsSync(join(jcoNativeDir, 'libsapjco3.so'))
    || !existsSync(sapcrypto)
    || !existsSync(sapgenpse)
    || !existsSync(cryptoKernel)) {
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
    metadataDir
  };
}

function writeJson(path: string, value: unknown): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, JSON.stringify(value, null, 2));
}

function homeDestinationsDir(): string {
  return join(homedir(), '.openadt', 'destinations');
}

function ensureProjectDestinations(root: string): void {
  const sourceDir = homeDestinationsDir();
  if (!existsSync(sourceDir)) {
    return;
  }

  const targetDir = join(root, '.openadt', 'destinations');
  mkdirSync(targetDir, { recursive: true });

  for (const file of walkFiles(targetDir)) {
    if (file.split(/[\\/]/).at(-1)?.startsWith('generated-') && file.endsWith('.openadt.toml')) {
      rmSync(file, { force: true });
    }
  }

  for (const file of walkFiles(sourceDir).sort()) {
    const name = file.split(/[\\/]/).at(-1);
    if (!name?.endsWith('.openadt.toml')) {
      continue;
    }
    copyFileSync(file, join(targetDir, `generated-${name}`));
  }
}

function writeDevcontainerRuntime(path: string, containerWorkspace: string): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(
    path,
    [
      'version = 1',
      '',
      '[runtime]',
      `jco_jar = "${containerWorkspace}/.devcontainer/dist/jco/sapjco3.jar"`,
      `jco_native_dir = "${containerWorkspace}/.devcontainer/dist/jco"`,
      `sapcrypto = "${containerWorkspace}/.devcontainer/dist/snc/libsapcrypto.so"`,
      ''
    ].join('\n')
  );
}

function writeDevcontainerEntrypoint(path: string): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(
    path,
    [
      'version = 1',
      '',
      '[merge]',
      'strategy = "last-wins"',
      'includes = [',
      '  "../.openadt/destinations/*.openadt.toml",',
      '  "runtime.openadt.toml"',
      ']',
      ''
    ].join('\n')
  );
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  const root = repoRoot();
  const sourceRoot = resolve(args.sourceRoot ?? defaultSourceRoot());
  const containerWorkspace = resolveContainerWorkspace(root, args.containerWorkspace);
  const devcontainerConfigPath = join(root, '.devcontainer', 'openadt-config.toml');
  const devcontainerRuntimePath = join(root, '.devcontainer', 'runtime.openadt.toml');
  const staged = detectStagedRuntime(root);

  if (staged) {
    ensureProjectDestinations(root);
    writeDevcontainerRuntime(devcontainerRuntimePath, containerWorkspace);
    writeDevcontainerEntrypoint(devcontainerConfigPath);
    console.log(`Reused staged Linux SAP runtime under ${staged.stageRoot}`);
    console.log(`Updated devcontainer config entrypoint at ${devcontainerConfigPath}`);
    return;
  }

  if (!existsSync(sourceRoot)) {
    if (args.nonInteractive) {
      throw new Error(`Source root not found: ${sourceRoot}`);
    }
    const entered = await promptValue('Enter source folder with SAP archives', sourceRoot);
    if (!entered || !existsSync(resolve(entered))) {
      throw new Error(`Source root not found: ${entered}`);
    }
  }

  const effectiveSourceRoot = existsSync(sourceRoot) ? sourceRoot : resolve(await promptValue('Enter source folder with SAP archives', sourceRoot));
  const jcoArchive = await resolveInputFile(args.jcoArchive, effectiveSourceRoot, /^sapjco31.*\.zip$/i, 'Linux JCo archive', args.nonInteractive);
  const cryptoArchive = await resolveInputFile(args.cryptoArchive, effectiveSourceRoot, /^SAPCRYPTOLIB.*\.SAR$/i, 'Linux CryptoLib SAR archive', args.nonInteractive);
  const sapcar = await resolveInputFile(args.sapcarPath, effectiveSourceRoot, /^SAPCAR.*\.(EXE|exe)$/i, 'SAPCAR executable', args.nonInteractive);

  const tempRoot = repoTempRoot(root);
  recreateDir(tempRoot);
  try {
    const jcoFiles = await expandJcoArchive(jcoArchive, tempRoot);
    const cryptoFiles = await expandCryptoArchive(cryptoArchive, sapcar, tempRoot);
    const stage = stageRuntime(root, jcoFiles, cryptoFiles);

    writeJson(join(stage.metadataDir, 'manifest.json'), {
      generated_at_utc: new Date().toISOString(),
      container_workspace: containerWorkspace,
      source: {
        jco_archive: jcoArchive,
        crypto_archive: cryptoArchive,
        sapcar
      },
      staged: {
        jco_jar: stage.jcoJar,
        jco_native_dir: stage.jcoNativeDir,
        snc_dir: stage.sncDir,
        sapcrypto: stage.sapcrypto,
        sapgenpse: stage.sapgenpse,
        secudir: stage.secudir
      }
    });

    ensureProjectDestinations(root);
    writeDevcontainerRuntime(devcontainerRuntimePath, containerWorkspace);
    writeDevcontainerEntrypoint(devcontainerConfigPath);

    console.log(`Staged Linux SAP runtime under ${stage.stageRoot}`);
    console.log(`Updated devcontainer config entrypoint at ${devcontainerConfigPath}`);
    console.log(`Manifest written to ${join(stage.metadataDir, 'manifest.json')}`);
  } finally {
    rmSync(tempRoot, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
