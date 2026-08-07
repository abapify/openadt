import AdmZip from 'adm-zip'
import { createHash } from 'node:crypto'
import {
  chmodSync,
  cpSync,
  existsSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import { packageMcpBinary, type FileChecksum } from './mcp-package.ts'

const root = resolve(import.meta.dir, '../../..')
const cliDir = join(root, 'apps/openadt-cli')
const pomContent = readFileSync(join(root, 'pom.xml'), 'utf8')

const versionArg = process.argv.find((a) => a.startsWith('--version='))?.split('=')[1]
const version = versionArg ?? process.env.OPENADT_VERSION ?? readPomVersion()

function readPomVersion(): string {
  const match = /<artifactId>openadt-parent<\/artifactId>\s+<version>([^<]+)<\/version>/.exec(
    pomContent
  )
  if (!match) {
    throw new Error('Could not read version from pom.xml')
  }
  return match[1].trim().replace(/-SNAPSHOT$/, '')
}

const jarFile = (() => {
  const plain = join(cliDir, 'target', `openadt-${version}.jar`)
  if (existsSync(plain)) {
    return plain
  }
  const snapshot = join(cliDir, 'target', `openadt-${version}-SNAPSHOT.jar`)
  if (existsSync(snapshot)) {
    return snapshot
  }
  throw new Error(
    `Neither openadt-${version}.jar nor openadt-${version}-SNAPSHOT.jar found under ${join(cliDir, 'target')}. Run 'mvn package' first.`
  )
})()
const jarPath = jarFile
const distDir = join(root, 'packaging/dist')
const stageDir = join(distDir, `openadt-${version}`)
const zipName = `openadt-${version}.zip`
const zipPath = join(distDir, zipName)

function sha256File(opts: FileChecksum): string {
  return createHash('sha256').update(readFileSync(opts.filePath)).digest('hex').toLowerCase()
}

function buildWindowsExe(opts: BuildTarget): void {
  const launcherDir = join(root, 'packaging/windows/launcher')
  const go = spawnSync('go', ['build', '-o', opts.target, '.'], {
    cwd: launcherDir,
    stdio: 'pipe',
  })
  if (go.status === 0) {
    return
  }
  if (go.stderr) {
    const stderr = go.stderr.toString().trim()
    if (stderr.length > 0) {
      console.error(stderr)
    }
  }

  const dotnet = spawnSync(
    'dotnet',
    [
      'publish',
      join(root, 'packaging/windows/launcher/OpenAdtLauncher.csproj'),
      '-c',
      'Release',
      '-o',
      dirname(opts.target),
      '/p:AssemblyName=openadt',
    ],
    { stdio: 'inherit' }
  )
  if (dotnet.status !== 0) {
    throw new Error('Failed to build openadt.exe. Install Go or .NET SDK (dotnet) on PATH.')
  }
}

function writeLaunchers(opts: LaunchersOutput): void {
  mkdirSync(join(opts.base, 'bin'), { recursive: true })

  cpSync(
    join(root, 'packaging/windows/openadt-launcher.ps1'),
    join(opts.base, 'bin/openadt-launcher.ps1')
  )
  cpSync(
    join(root, 'packaging/windows/prepare-openadt-runtime.ps1'),
    join(opts.base, 'bin/prepare-openadt-runtime.ps1')
  )
  cpSync(
    join(root, 'packaging/scoop/post-install.ps1'),
    join(opts.base, 'bin/scoop-post-install.ps1')
  )
  cpSync(
    join(root, 'packaging/unix/openadt-launcher.sh'),
    join(opts.base, 'bin/openadt-launcher.sh')
  )
  chmodSync(join(opts.base, 'bin/openadt-launcher.sh'), 0o755)

  writeFileSync(
    join(opts.base, 'bin/openadt.cmd'),
    `@echo off\r\nsetlocal EnableDelayedExpansion\r\nset "OPENADT_HOME=%~dp0.."\r\nset "OPENADT_ARG_COUNT=0"\r\n:openadt_args\r\nif "%~1"=="" goto openadt_run\r\nset "OPENADT_ARG_!OPENADT_ARG_COUNT!=%~1"\r\nset /a OPENADT_ARG_COUNT+=1\r\nshift\r\ngoto openadt_args\r\n:openadt_run\r\npowershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0openadt-launcher.ps1"\r\nexit /b %ERRORLEVEL%\r\n`
  )

  writeFileSync(
    join(opts.base, 'bin/openadt.ps1'),
    `param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $OpenAdtArgs
)
$env:OPENADT_HOME = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'openadt-launcher.ps1') @OpenAdtArgs
exit $LASTEXITCODE
`
  )

  // Delegates to openadt-launcher.sh, which picks the lite jar or the SDK runtime jar per
  // subcommand. Running openadt.jar directly would leave SDK transport unavailable.
  writeFileSync(
    join(opts.base, 'bin/openadt'),
    `#!/usr/bin/env bash
set -uo pipefail
OPENADT_HOME="$(cd "$(dirname "$0")/.." && pwd)"
export OPENADT_HOME
exec "$OPENADT_HOME/bin/openadt-launcher.sh" "$@"
`,
    { mode: 0o755 }
  )
}

type BuildTarget = {
  target: string
}

type LaunchersOutput = {
  base: string
}

rmSync(stageDir, { recursive: true, force: true })
mkdirSync(stageDir, { recursive: true })

if (!existsSync(jarPath)) {
  throw new Error(
    `Missing jar. Run: cd apps/openadt-cli && mvnw -Pdistribution -Dopenadt.distribution=true package -DskipTests`
  )
}

cpSync(jarPath, join(stageDir, 'openadt.jar'))
writeFileSync(join(stageDir, 'VERSION'), `${version}\n`)
cpSync(join(root, 'LICENSE'), join(stageDir, 'LICENSE'))

writeLaunchers({ base: stageDir })
if (process.platform === 'win32' || process.env.OPENADT_PACKAGE_WIN_EXE === '1') {
  buildWindowsExe({ target: join(stageDir, 'openadt.exe') })
  for (const extra of ['openadt.pdb', 'openadt.deps.json', 'openadt.runtimeconfig.json']) {
    const path = join(stageDir, extra)
    if (existsSync(path)) {
      rmSync(path)
    }
  }
}

const zip = new AdmZip()
zip.addLocalFolder(stageDir, `openadt-${version}`)
zip.writeZip(zipPath)

const sha256 = sha256File({ filePath: zipPath })
writeFileSync(`${zipPath}.sha256`, `${sha256}  ${zipName}\n`)
updateHomebrewSha256(sha256)
updateScoopSha256(sha256)

console.log(`Packaged ${zipPath}`)
console.log(`SHA256 ${sha256}`)

packageMcpBinary({ root, distDir, version, sha256File })

function updateHomebrewSha256(sha256: string): void {
  const formulaPath = join(root, 'packaging/homebrew/openadt.rb')
  let ruby = readFileSync(formulaPath, 'utf8')
  ruby = ruby.replace(/sha256 "[^"]+"/, `sha256 "${sha256.toLowerCase()}"`)
  writeFileSync(formulaPath, ruby)
  syncHomebrewTapFormula(formulaPath, 'openadt')
}

function syncHomebrewTapFormula(formulaPath: string, product: string): void {
  const tapPath = join(root, `Formula/${product}.rb`)
  mkdirSync(dirname(tapPath), { recursive: true })
  const buf = readFileSync(formulaPath)
  writeFileSync(tapPath, buf)
}

function updateScoopSha256(sha256: string): void {
  const manifestPath = join(root, 'packaging/scoop/openadt.json')
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8')) as {
    architecture: { '64bit': { url: string; hash: string } }
  }
  manifest.architecture['64bit'].url =
    `https://github.com/abapify/openadt/releases/download/v${version}/${zipName}`
  manifest.architecture['64bit'].hash = sha256.toLowerCase()
  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 4)}\n`)
}
