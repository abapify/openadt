package org.openadt.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdtBundleResolverTest {
    @TempDir
    Path pluginsDir;

    /** Writes one jar per bundle at its baseline version, i.e. a pool matching the tested combination. */
    private void writeBaselinePool() throws Exception {
        for (AdtBundleResolver.Bundle bundle : AdtBundleResolver.BUNDLES) {
            Files.createFile(pluginsDir.resolve(bundle.fileNameFor(bundle.baselineVersion())));
        }
    }

    @Test
    void resolvesCompletePoolWithoutDrift() throws Exception {
        writeBaselinePool();

        AdtBundleResolver.Resolution resolution = AdtBundleResolver.resolve(pluginsDir);

        assertTrue(resolution.isComplete());
        assertTrue(resolution.drift().isEmpty(), () -> "unexpected drift: " + resolution.drift());
        assertEquals(AdtBundleResolver.BUNDLES.size(), resolution.properties().size());
    }

    @Test
    void reportsMissingBundlesByFileName() {
        AdtBundleResolver.Resolution resolution = AdtBundleResolver.resolve(pluginsDir);

        assertFalse(resolution.isComplete());
        assertEquals(AdtBundleResolver.BUNDLES.size(), resolution.missing().size());
        assertTrue(resolution.missing().stream().anyMatch(name -> name.startsWith("com.sap.adt.communication_")));
    }

    @Test
    void picksNewestVersionAndReportsDrift() throws Exception {
        writeBaselinePool();
        // Mirrors a real pool holding both an older IDE's bundles and a newer update.
        Files.createFile(pluginsDir.resolve("com.sap.adt.communication_3.60.2.jar"));
        Files.createFile(pluginsDir.resolve("com.sap.adt.communication_3.56.0.jar"));

        AdtBundleResolver.Resolution resolution = AdtBundleResolver.resolve(pluginsDir);

        assertTrue(resolution.isComplete());
        assertEquals(
            pluginsDir.resolve("com.sap.adt.communication_3.60.2.jar").toAbsolutePath(),
            resolution.properties().get("adt.jar.communication")
        );
        assertTrue(
            resolution.drift().stream().anyMatch(d -> d.contains("com.sap.adt.communication")
                && d.contains("3.58.0")
                && d.contains("3.60.2")),
            () -> "expected drift entry for communication, got: " + resolution.drift()
        );
    }

    @Test
    void ordersEclipseQualifiersByNumericSegments() throws Exception {
        writeBaselinePool();
        Files.createFile(pluginsDir.resolve("org.eclipse.osgi_3.24.200.v20260515-1403.jar"));
        Files.createFile(pluginsDir.resolve("org.eclipse.osgi_3.24.0.v20251126-0427.jar"));

        AdtBundleResolver.Resolution resolution = AdtBundleResolver.resolve(pluginsDir);

        assertEquals(
            pluginsDir.resolve("org.eclipse.osgi_3.24.200.v20260515-1403.jar").toAbsolutePath(),
            resolution.properties().get("adt.jar.osgi")
        );
    }

    @Test
    void prefixMatchIsBoundedByUnderscore() throws Exception {
        // Only the .model bundle exists; the bare "destinations" prefix must not match it.
        Files.createFile(pluginsDir.resolve("com.sap.adt.destinations.model_3.58.0.jar"));

        assertTrue(AdtBundleResolver.findNewest(pluginsDir, "com.sap.adt.destinations").isEmpty());
        assertTrue(AdtBundleResolver.findNewest(pluginsDir, "com.sap.adt.destinations.model").isPresent());
    }

    @Test
    void jcoPrefixDoesNotMatchPlatformOrEclipseBundles() throws Exception {
        Files.createFile(pluginsDir.resolve("com.sap.conn.jco.eclipse_1.32.0.jar"));
        Files.createFile(pluginsDir.resolve("com.sap.conn.jco.macosx.aarch64_3.1.12.jar"));

        assertTrue(AdtBundleResolver.findNewest(pluginsDir, "com.sap.conn.jco").isEmpty());

        Files.createFile(pluginsDir.resolve("com.sap.conn.jco_3.1.13.jar"));
        assertEquals(
            "com.sap.conn.jco_3.1.13.jar",
            AdtBundleResolver.findNewest(pluginsDir, "com.sap.conn.jco").orElseThrow()
                .getFileName().toString()
        );
    }

    @Test
    void databindingPrefixDoesNotMatchSubBundles() throws Exception {
        Files.createFile(pluginsDir.resolve("org.eclipse.core.databinding.beans_1.10.500.v1.jar"));
        Files.createFile(pluginsDir.resolve("org.eclipse.core.databinding.property_1.10.500.v1.jar"));

        assertTrue(AdtBundleResolver.findNewest(pluginsDir, "org.eclipse.core.databinding").isEmpty());
    }

    @Test
    void ignoresNonJarFiles() throws Exception {
        Files.createFile(pluginsDir.resolve("com.sap.adt.util_3.58.0.jar.sha1"));
        Files.createDirectory(pluginsDir.resolve("com.sap.adt.util_3.58.0"));

        assertTrue(AdtBundleResolver.findNewest(pluginsDir, "com.sap.adt.util").isEmpty());
    }

    @Test
    void missingDirectoryResolvesToNothing() {
        AdtBundleResolver.Resolution resolution = AdtBundleResolver.resolve(pluginsDir.resolve("absent"));

        assertFalse(resolution.isComplete());
        assertTrue(resolution.properties().isEmpty());
    }

    @Test
    void everyBundleHasDistinctPropertyAndPrefix() {
        long distinctProperties = AdtBundleResolver.BUNDLES.stream()
            .map(AdtBundleResolver.Bundle::propertyName).distinct().count();
        long distinctPrefixes = AdtBundleResolver.BUNDLES.stream()
            .map(AdtBundleResolver.Bundle::bundlePrefix).distinct().count();

        assertEquals(AdtBundleResolver.BUNDLES.size(), distinctProperties);
        assertEquals(AdtBundleResolver.BUNDLES.size(), distinctPrefixes);
    }
}
