package org.openadt.cli;

import picocli.CommandLine;

/** Reads {@code Implementation-Version} from the packaged JAR manifest (Maven {@code ${project.version}}). */
final class OpenAdtVersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
        String version = OpenAdtCommand.class.getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) {
            version = "dev";
        }
        return new String[] {"openadt " + version};
    }
}
