package org.openadt.bootstrap;

import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.destination.SapRulesDiscoveryHelper;

import java.nio.file.Path;
import java.util.List;

public class SapRulesDetector implements SystemDetector {
    private final List<Path> sapRulesFiles;

    public SapRulesDetector() {
        this(SetupPathLocator.sapRulesFiles());
    }

    SapRulesDetector(List<Path> sapRulesFiles) {
        this.sapRulesFiles = List.copyOf(sapRulesFiles);
    }

    @Override
    public List<SystemProfile> detect() {
        return SapRulesDiscoveryHelper.detect(sapRulesFiles);
    }
}
