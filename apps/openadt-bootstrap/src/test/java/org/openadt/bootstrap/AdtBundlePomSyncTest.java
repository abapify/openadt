package org.openadt.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdtBundleResolver} feeds {@code -Dadt.jar.*} properties to the {@code sap-sdk} Maven profile.
 * A property added on one side only would silently fall back to the pinned default (or fail to
 * resolve), so the two lists are pinned together here.
 */
class AdtBundlePomSyncTest {
    private static final Pattern PROPERTY_DECLARATION = Pattern.compile("<(adt\\.jar\\.[A-Za-z0-9.]+)>");
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{(adt\\.jar\\.[A-Za-z0-9.]+)}");

    private static Path sapAdtPom() {
        Path current = Path.of("").toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path pom = candidate.resolve("apps/openadt-sap-adt/pom.xml");
            if (Files.isRegularFile(pom)) {
                return pom;
            }
        }
        throw new IllegalStateException("Could not locate apps/openadt-sap-adt/pom.xml from " + current);
    }

    private static Set<String> matches(String content, Pattern pattern) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    void pomDeclaresExactlyTheResolvedBundleProperties() throws IOException {
        String pom = Files.readString(sapAdtPom());
        Set<String> expected = new LinkedHashSet<>(
            AdtBundleResolver.BUNDLES.stream().map(AdtBundleResolver.Bundle::propertyName).toList()
        );

        assertEquals(expected, matches(pom, PROPERTY_DECLARATION), "pom <properties> out of sync with AdtBundleResolver");
    }

    @Test
    void everyDeclaredPropertyIsUsedBySomeSystemPath() throws IOException {
        String pom = Files.readString(sapAdtPom());

        assertEquals(
            matches(pom, PROPERTY_DECLARATION),
            matches(pom, PROPERTY_REFERENCE),
            "each adt.jar.* property must be declared and referenced by a systemPath"
        );
    }

    @Test
    void pomDefaultsMatchResolverBaselineVersions() throws IOException {
        String pom = Files.readString(sapAdtPom());

        for (AdtBundleResolver.Bundle bundle : AdtBundleResolver.BUNDLES) {
            String expectedDefault = "<" + bundle.propertyName() + ">${adt.plugins.dir}/"
                + bundle.fileNameFor(bundle.baselineVersion())
                + "</" + bundle.propertyName() + ">";
            assertTrue(
                pom.contains(expectedDefault),
                () -> "pom default missing or stale for " + bundle.propertyName() + "; expected " + expectedDefault
            );
        }
    }

    @Test
    void noSystemPathStillPinsAJarFileNameDirectly() throws IOException {
        String pom = Files.readString(sapAdtPom());
        Matcher matcher = Pattern.compile("<systemPath>([^<]+)</systemPath>").matcher(pom);

        while (matcher.find()) {
            String systemPath = matcher.group(1);
            assertTrue(
                systemPath.startsWith("${adt.jar."),
                () -> "systemPath must go through an adt.jar.* property, found: " + systemPath
            );
        }
    }
}
