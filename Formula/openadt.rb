class Openadt < Formula
  desc "Local bridge for SAP ADT traffic"
  homepage "https://github.com/abapify/openadt"
  license "Apache-2.0"

  # Stable: prebuilt zip from GitHub Releases (sha256 updated by package:release on v1.2.6).
  STABLE = "1.2.6"
  url "https://github.com/abapify/openadt/releases/download/v#{STABLE}/openadt-#{STABLE}.zip"
  sha256 "8d55d502db3f430ddf72601e9884e1d37741070e3bc10990b67679e68143f6b3"
  version STABLE

  head "https://github.com/abapify/openadt.git", branch: "main"

  depends_on "openjdk@21"
  depends_on "maven" => :build

  def install
    if build.stable?
      libexec.install "openadt-#{version}/openadt.jar" => "openadt.jar"
    else
      cd "apps/openadt-cli" do
        system Formula["maven"].bin/"mvn", "-q", "-Pdistribution", "-Dopenadt.distribution=true", "package", "-DskipTests"
        built_jar = Dir["target/openadt-*.jar"]
          .find { |path| !path.end_with?("-sources.jar", "-javadoc.jar") }
        odie "Could not find built OpenADT jar in target/" if built_jar.nil?
        libexec.install built_jar => "openadt.jar"
      end
    end
    (bin/"openadt").write <<~SH
      #!/bin/bash
      exec "#{Formula["openjdk@21"].bin}/java" -jar "#{libexec}/openadt.jar" "$@"
    SH
  end

  test do
    assert_match "Usage:", shell_output("#{bin}/openadt 2>&1", 2)
  end
end
