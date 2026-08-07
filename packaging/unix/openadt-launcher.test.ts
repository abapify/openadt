import { describe, expect, test } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { chmodSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const root = resolve(import.meta.dir, '../..')
const unixLauncher = join(root, 'packaging/unix/openadt-launcher.sh')
const windowsLauncher = join(root, 'packaging/windows/openadt-launcher.ps1')

/**
 * Stages a fake install: a launcher, a VERSION file, and a `java` stub that just echoes how it was
 * invoked. Lets the dispatch table be asserted without a JDK, SAP bundles, or a real system.
 */
function stageInstall(): { home: string; binDir: string; env: Record<string, string> } {
  const home = join(tmpdir(), `openadt-launcher-test-${Math.random().toString(36).slice(2)}`)
  const binDir = join(home, 'bin')
  mkdirSync(binDir, { recursive: true })
  writeFileSync(join(home, 'openadt.jar'), 'not-a-real-jar')
  writeFileSync(join(home, 'VERSION'), '9.9.9\n')
  writeFileSync(join(binDir, 'openadt-launcher.sh'), readFileSync(unixLauncher))
  chmodSync(join(binDir, 'openadt-launcher.sh'), 0o755)

  const fakeJavaHome = join(home, 'jdk')
  mkdirSync(join(fakeJavaHome, 'bin'), { recursive: true })
  const javaStub = join(fakeJavaHome, 'bin/java')
  writeFileSync(javaStub, '#!/usr/bin/env bash\necho "JAVA_ARGS: $*"\n')
  chmodSync(javaStub, 0o755)

  return { home, binDir, env: { OPENADT_HOME: home, JAVA_HOME: fakeJavaHome } }
}

function run(args: string[], extraEnv: Record<string, string> = {}) {
  const staged = stageInstall()
  try {
    const result = spawnSync('bash', [join(staged.binDir, 'openadt-launcher.sh'), ...args], {
      encoding: 'utf8',
      env: { ...process.env, ...staged.env, ...extraEnv, HOME: staged.home },
    })
    return `${result.stdout ?? ''}${result.stderr ?? ''}`
  } finally {
    rmSync(staged.home, { recursive: true, force: true })
  }
}

describe('openadt-launcher.sh', () => {
  test('is valid bash', () => {
    const result = spawnSync('bash', ['-n', unixLauncher], { encoding: 'utf8' })
    expect(result.status).toBe(0)
  })

  test('runs non-SDK commands from the lite jar', () => {
    // -jar means the lite distribution jar; SDK mode uses -cp plus a main class.
    expect(run(['config'])).toContain('-jar')
    expect(run(['config'])).not.toContain('OpenAdtCommand')
  })

  test('runs with no arguments from the lite jar', () => {
    expect(run([])).toContain('-jar')
  })

  test('builds the SDK runtime before an SDK command when it is missing', () => {
    // No ~/.openadt/runtime exists in the staged HOME, so the launcher must build it first.
    const output = run(['discovery', 'DEV'])
    expect(output).toContain('preparing SAP SDK runtime')
    expect(output).toContain('config build')
  })

  test('reports a missing lite jar instead of failing obscurely', () => {
    const staged = stageInstall()
    try {
      rmSync(join(staged.home, 'openadt.jar'))
      const result = spawnSync('bash', [join(staged.binDir, 'openadt-launcher.sh'), 'config'], {
        encoding: 'utf8',
        env: { ...process.env, ...staged.env, HOME: staged.home },
      })
      expect(`${result.stdout}${result.stderr}`).toContain('reinstall OpenADT')
      expect(result.status).not.toBe(0)
    } finally {
      rmSync(staged.home, { recursive: true, force: true })
    }
  })

  test('locates the runtime under the JVM user.home, not $HOME', () => {
    // `config build` writes the runtime under Java's user.home. Where that differs from $HOME
    // (sudo without -H, or a JVM reading the passwd database), reading $HOME would send the
    // launcher to a directory the build never wrote.
    const staged = stageInstall()
    const javaUserHome = join(staged.home, 'jvm-home')
    const runtime = join(javaUserHome, '.openadt/runtime')
    mkdirSync(join(runtime, 'sap-lib'), { recursive: true })
    writeFileSync(join(runtime, 'openadt-full.jar'), 'full')
    writeFileSync(join(runtime, 'version.txt'), '9.9.9\n')
    // A java stub that reports a user.home different from $HOME.
    writeFileSync(
      join(staged.home, 'jdk/bin/java'),
      `#!/usr/bin/env bash\nif [[ "$*" == *-XshowSettings:properties* ]]; then\n  echo "    user.home = ${javaUserHome}" >&2\n  exit 0\nfi\necho "JAVA_ARGS: $*"\n`
    )
    chmodSync(join(staged.home, 'jdk/bin/java'), 0o755)
    try {
      const result = spawnSync(
        'bash',
        [join(staged.binDir, 'openadt-launcher.sh'), 'discovery', 'DEV'],
        {
          encoding: 'utf8',
          env: { ...process.env, ...staged.env, HOME: staged.home },
        }
      )
      const output = `${result.stdout ?? ''}${result.stderr ?? ''}`
      // Runtime found where the JVM says it is, so no rebuild and the jar is on the classpath.
      expect(output).not.toContain('preparing SAP SDK runtime')
      expect(output).toContain(join(runtime, 'openadt-full.jar'))
    } finally {
      rmSync(staged.home, { recursive: true, force: true })
    }
  })

  test('rebuilds when sap-lib is missing even if the jar and marker are present', () => {
    // Mirrors SetupRuntimePreparer.runtimeJarReady: the jar's manifest Class-Path needs sap-lib/.
    const staged = stageInstall()
    const runtime = join(staged.home, '.openadt/runtime')
    mkdirSync(runtime, { recursive: true })
    writeFileSync(join(runtime, 'openadt-full.jar'), 'full')
    writeFileSync(join(runtime, 'version.txt'), '9.9.9\n')
    try {
      const result = spawnSync(
        'bash',
        [join(staged.binDir, 'openadt-launcher.sh'), 'discovery', 'DEV'],
        {
          encoding: 'utf8',
          env: { ...process.env, ...staged.env, HOME: staged.home },
        }
      )
      const output = `${result.stdout ?? ''}${result.stderr ?? ''}`
      expect(output).toContain('preparing SAP SDK runtime')
    } finally {
      rmSync(staged.home, { recursive: true, force: true })
    }
  })

  test('falls back to $HOME when the JVM does not report user.home', () => {
    // The java stub in stageInstall echoes its arguments and never prints user.home.
    const output = run(['discovery', 'DEV'])
    expect(output).toContain('preparing SAP SDK runtime')
  })

  test('dispatches the same subcommands to the SDK as the Windows launcher', () => {
    // Parity guard: the two launchers must not drift apart.
    const ps1 = readFileSync(windowsLauncher, 'utf8')
    const windowsList = /\$subcommand -in @\(([^)]*)\)/.exec(ps1)
    expect(windowsList).not.toBeNull()
    const windowsCommands = [...windowsList![1].matchAll(/"([a-z]+)"/g)].map((m) => m[1]).sort()

    const sh = readFileSync(unixLauncher, 'utf8')
    const unixList = /^SDK_COMMANDS="([^"]*)"$/m.exec(sh)
    expect(unixList).not.toBeNull()
    const unixCommands = unixList![1].trim().split(/\s+/).sort()

    expect(unixCommands).toEqual(windowsCommands)
  })
})

describe('release packaging', () => {
  test('ships the unix launcher and delegates bin/openadt to it', () => {
    const main = readFileSync(join(root, 'tools/package-release/src/main.ts'), 'utf8')
    expect(main).toContain('packaging/unix/openadt-launcher.sh')
    expect(main).toContain('bin/openadt-launcher.sh')
    // bin/openadt must delegate; running openadt.jar directly loses SDK transport.
    expect(main).not.toContain('exec java -jar "$OPENADT_HOME/openadt.jar"')
  })

  test('homebrew formula installs the launcher and exports JAVA_HOME', () => {
    const formula = readFileSync(join(root, 'packaging/homebrew/openadt.rb'), 'utf8')
    expect(formula).toContain('bin/openadt-launcher.sh')
    expect(formula).toContain('JAVA_HOME')
  })
})
