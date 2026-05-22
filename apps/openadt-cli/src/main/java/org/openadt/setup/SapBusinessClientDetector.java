package org.openadt.setup;

import org.openadt.core.SystemProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SapBusinessClientDetector implements SystemDetector {
    private final List<Path> configPaths;

    public SapBusinessClientDetector() {
        this(SetupPathLocator.sapBusinessClientPaths());
    }

    SapBusinessClientDetector(List<Path> configPaths) {
        this.configPaths = List.copyOf(configPaths);
    }

    @Override
    public List<SystemProfile> detect() {
        for (Path path : configPaths) {
            if (Files.exists(path)) {
                return List.of();
            }
        }
        return List.of();
    }
}
