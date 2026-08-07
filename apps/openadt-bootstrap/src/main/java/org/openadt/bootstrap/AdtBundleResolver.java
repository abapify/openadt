package org.openadt.bootstrap;

import org.openadt.config.OsgiVersions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resolves the SAP ADT, JCo and Eclipse platform bundles that the {@code sap-sdk} Maven profile
 * compiles against.
 *
 * <p>The profile cannot glob: a {@code <systemPath>} must name one file. Pinning exact file names
 * means the build only ever works against the single ADT release the pin was written for — any other
 * Eclipse installation fails dependency resolution before compiling a line. This resolver instead
 * matches each bundle by its symbolic prefix and takes the newest version present, then feeds the
 * result to Maven as {@code -Dadt.jar.*} properties.
 *
 * <p>Baseline versions are recorded so drift from the tested combination is reported rather than
 * silently accepted.
 */
public final class AdtBundleResolver {
    /**
     * ADT bundle baseline shared by the com.sap.adt.* plugins. Kept in one place so every ADT
     * bundle is locked to the same tested version.
     */
    private static final String ADT_BASELINE_VERSION = "3.58.0";

    /** One {@code sap-sdk} system-scope dependency. */
    record Bundle(String propertyName, String bundlePrefix, String baselineVersion) {
        String fileNameFor(String version) {
            return bundlePrefix + "_" + version + ".jar";
        }
    }

    /**
     * Must stay in step with the {@code adt.jar.*} properties in
     * {@code apps/openadt-sap-adt/pom.xml}; {@code AdtBundlePomSyncTest} enforces that.
     */
    static final List<Bundle> BUNDLES = List.of(
        adt("adt.jar.communication", "com.sap.adt.communication"),
        adt("adt.jar.compatibility", "com.sap.adt.compatibility"),
        adt("adt.jar.destinations", "com.sap.adt.destinations"),
        adt("adt.jar.destinations.model", "com.sap.adt.destinations.model"),
        adt("adt.jar.logging", "com.sap.adt.logging"),
        adt("adt.jar.util", "com.sap.adt.util"),
        adt("adt.jar.transport", "com.sap.adt.transport"),
        new Bundle("adt.jar.jco", "com.sap.conn.jco", "3.1.13"),
        new Bundle("adt.jar.jco.eclipse", "com.sap.conn.jco.eclipse", "1.32.0"),
        new Bundle("adt.jar.core.runtime", "org.eclipse.core.runtime", "3.34.200.v20251220-0953"),
        new Bundle("adt.jar.core.resources", "org.eclipse.core.resources", "3.23.200.v20251217-0810"),
        new Bundle("adt.jar.core.commands", "org.eclipse.core.commands", "3.12.500.v20251103-0733"),
        new Bundle("adt.jar.core.databinding", "org.eclipse.core.databinding", "1.13.700.v20251023-1511"),
        new Bundle("adt.jar.core.databinding.beans", "org.eclipse.core.databinding.beans", "1.10.500.v20250916-0941"),
        new Bundle(
            "adt.jar.core.databinding.observable",
            "org.eclipse.core.databinding.observable",
            "1.13.500.v20251103-0735"
        ),
        new Bundle(
            "adt.jar.core.databinding.property",
            "org.eclipse.core.databinding.property",
            "1.10.500.v20250916-0931"
        ),
        new Bundle("adt.jar.core.jobs", "org.eclipse.core.jobs", "3.15.700.v20250725-1147"),
        new Bundle("adt.jar.core.net", "org.eclipse.core.net", "1.5.800.v20250613-1119"),
        new Bundle("adt.jar.equinox.common", "org.eclipse.equinox.common", "3.20.300.v20251111-0312"),
        new Bundle("adt.jar.equinox.registry", "org.eclipse.equinox.registry", "3.12.600.v20250906-0651"),
        // Runtime-only: the discovery document is parsed with EMF (XMLProcessor and friends).
        new Bundle("adt.jar.emf.common", "org.eclipse.emf.common", "2.45.0.v20260311-1301"),
        new Bundle("adt.jar.emf.ecore", "org.eclipse.emf.ecore", "2.42.0.v20251210-1145"),
        new Bundle("adt.jar.emf.ecore.xmi", "org.eclipse.emf.ecore.xmi", "2.40.0.v20251210-1145"),
        new Bundle("adt.jar.osgi", "org.eclipse.osgi", "3.24.100.v20251215-1416"),
        new Bundle("adt.jar.osgi.util", "org.eclipse.osgi.util", "3.7.400.v20250516-0916"),
        new Bundle("adt.jar.service.prefs", "org.osgi.service.prefs", "1.1.2.202109301733")
    );

    /** Outcome of resolving every bundle against one plugins directory. */
    public record Resolution(Map<String, Path> properties, List<String> drift, List<String> missing) {
        public boolean isComplete() {
            return missing.isEmpty();
        }
    }

    private AdtBundleResolver() {
    }

    private static Bundle adt(String propertyName, String bundlePrefix) {
        return new Bundle(propertyName, bundlePrefix, ADT_BASELINE_VERSION);
    }

    public static Resolution resolve(Path pluginsDir) {
        Map<String, Path> properties = new LinkedHashMap<>();
        List<String> drift = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (Bundle bundle : BUNDLES) {
            Optional<Path> match = findNewest(pluginsDir, bundle.bundlePrefix());
            if (match.isEmpty()) {
                missing.add(bundle.fileNameFor(bundle.baselineVersion()));
                continue;
            }
            Path jar = match.get();
            properties.put(bundle.propertyName(), jar.toAbsolutePath());
            String resolvedVersion = versionOf(jar, bundle.bundlePrefix());
            if (!bundle.baselineVersion().equals(resolvedVersion)) {
                drift.add(bundle.bundlePrefix() + ": expected " + bundle.baselineVersion()
                    + ", using " + resolvedVersion);
            }
        }
        return new Resolution(properties, drift, missing);
    }

    /**
     * Newest jar named {@code <prefix>_<version>.jar}. The underscore boundary matters: without it
     * {@code org.eclipse.core.databinding} would also match
     * {@code org.eclipse.core.databinding.beans}, and {@code com.sap.conn.jco} would match the
     * platform native bundles.
     */
    static Optional<Path> findNewest(Path pluginsDir, String bundlePrefix) {
        if (!Files.isDirectory(pluginsDir)) {
            return Optional.empty();
        }
        String prefix = bundlePrefix + "_";
        Path newest = null;
        String newestVersion = null;
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            for (Path candidate : stream.filter(Files::isRegularFile).toList()) {
                String name = candidate.getFileName().toString();
                if (!name.startsWith(prefix) || !name.endsWith(".jar")) {
                    continue;
                }
                String version = name.substring(prefix.length(), name.length() - ".jar".length());
                if (version.isEmpty()) {
                    continue;
                }
                if (newestVersion == null || OsgiVersions.compare(version, newestVersion) > 0) {
                    newest = candidate;
                    newestVersion = version;
                }
            }
        } catch (IOException unreadable) {
            return Optional.empty();
        }
        return Optional.ofNullable(newest);
    }

    private static String versionOf(Path jar, String bundlePrefix) {
        String name = jar.getFileName().toString();
        return name.substring(bundlePrefix.length() + 1, name.length() - ".jar".length());
    }
}
