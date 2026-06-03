class Openadt < Formula
  desc "Local bridge for SAP ADT traffic"
  homepage "https://github.com/abapify/openadt"
  license "Apache-2.0"

  # Stable: prebuilt zip from GitHub Releases.
  # STABLE and sha256 are refreshed by `bun run package:release`.
  STABLE = "1.2.7"
  url "https://github.com/abapify/openadt/releases/download/v#{STABLE}/openadt-#{STABLE}.zip"
  sha256 "98f884a700ca38c054dd298021959da0d3d0d378b174d58052bfe8b2ff3fe95a"
  version STABLE

  head "https://github.com/abapify/openadt.git", branch: "main"

  depends_on "openjdk@21"

  head do
    depends_on "maven" => :build
  end

  def install
    # openjdk@21 is keg-only; make it the active JDK for this build/install
    # (and for the wrapper script below) without pinning the Cellar path.
    ENV["JAVA_HOME"] = Formula["openjdk@21"].opt_prefix
    ENV.prepend_path "PATH", Formula["openjdk@21"].opt_bin

    if build.stable?
      jar = ["openadt-#{version}/openadt.jar", "openadt.jar"].find { |path| File.file?(path) }
      odie "Could not find openadt.jar in release zip" if jar.nil?
      libexec.install jar => "openadt.jar"
    else
      # HEAD build is a multi-module Maven reactor; build from the repo root
      # so sibling modules (openadt-config, openadt-sap-adt, openadt-bootstrap)
      # are resolved. Building from inside apps/openadt-cli would fail on a
      # clean checkout.
      system "mvn", "-q", "-f", "pom.xml",
             "-pl", "apps/openadt-cli", "-am",
             "-Pdistribution", "-Dopenadt.distribution=true",
             "package", "-DskipTests"
      built_jar = Dir["apps/openadt-cli/target/openadt-*.jar"]
        .find { |path| !path.end_with?("-sources.jar", "-javadoc.jar") }
      odie "Could not find built OpenADT jar in apps/openadt-cli/target/" if built_jar.nil?
      libexec.install built_jar => "openadt.jar"
    end

    (bin/"openadt").write <<~SH
      #!/bin/bash
      exec "#{Formula["openjdk@21"].opt_bin}/java" -jar "#{libexec}/openadt.jar" "$@"
    SH
    chmod 0755, bin/"openadt"
  end

  test do
    assert_match "Usage:", shell_output("#{bin}/openadt 2>&1", 2)
  end
end
