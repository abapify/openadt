class OpenadtMcp < Formula
  desc "SAP ADT MCP orchestrator (compiled Bun binary, no Java required for our code)"
  homepage "https://github.com/abapify/openadt"
  license "Apache-2.0"

  # Stable: prebuilt tarball from GitHub Releases.
  # STABLE and sha256 are refreshed by `bun run package:release` on the
  # darwin-arm64 runner (see `MCP_HOMEBREW_PLATFORM` in
  # tools/package-release/src/main.ts). darwin-x64 and linux-x64 archives
  # are produced by the release matrix but not yet wired into a per-platform
  # formula — install from the release tarball directly for now.
  STABLE = "3.0.1"
  url "https://github.com/abapify/openadt/releases/download/v#{STABLE}/openadt-mcp-#{STABLE}-darwin-arm64.tar.gz"
  sha256 "PLACEHOLDER_RUN_PACKAGE_RELEASE"
  version STABLE

  head "https://github.com/abapify/openadt.git", branch: "main"

  # openadt-mcp itself does not require openjdk. The adt-lsc SAP VS Code
  # extension bundles its own JRE. JDK 21 is only needed if you wire
  # openadt-mcp up to an adt-lsc install that needs an external runtime
  # (e.g. SNC). Documented in caveats below.

  def install
    bin.install "openadt-mcp"
  end

  test do
    assert_match "Usage:", shell_output("#{bin}/openadt-mcp 2>&1", 2)
  end

  def caveats
    <<~EOS
      openadt-mcp is the MCP orchestrator around adt-lsc (SAP ADT for VS Code).
      It has no Java dependency of its own; JDK 21 is only needed if adt-lsc
      is configured to use an external runtime (e.g. SNC).

      Install the SAP ADT for VS Code extension:
        https://marketplace.visualstudio.com/items?itemName=SAPSE.adt-vscode
    EOS
  end
end
