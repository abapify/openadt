package org.openadt.setup;

import org.openadt.core.SystemProfile;
import java.util.ArrayList;
import java.util.List;

public class SetupAnalyzer {
    public record SetupResult(List<SystemProfile> systems, List<String> warnings) {}

    public SetupResult analyze() {
        List<SystemProfile> systems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        new SapGuiLandscapeDetector().detect().forEach(systems::add);
        new SapBusinessClientDetector().detect().forEach(systems::add);
        new EclipseAdtDetector().detect().forEach(systems::add);

        SecureLoginDetector.DetectionResult slcResult = new SecureLoginDetector().detectSecureLogin();
        if (!slcResult.available()) {
            warnings.add("SAP Secure Login Client not detected at " + slcResult.url());
        }

        return new SetupResult(systems, warnings);
    }
}
