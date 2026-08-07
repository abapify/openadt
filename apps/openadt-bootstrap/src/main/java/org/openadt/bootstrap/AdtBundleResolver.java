package org.openadt.bootstrap;

import org.openadt.config.OsgiVersions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final String JAR_SUFFIX = ".jar";
    /** ADT ships its bundles as one versioned set, so they share a baseline. */
    private static final String ADT_BASELINE = "3.58.0";

    /**
     * Bundles that SAP releases as one versioned set. Every member must resolve to the same version:
     * mixing, say, {@code com.sap.adt.communication_3.58.0} with
     * {@code com.sap.adt.destinations_3.60.2} is not a combination SAP ships or tests, and the resulting
     * classpath can fail at link time rather than at resolution.
     *
     * <p>The Eclipse platform and EMF bundles version independently of one another, so no equivalent
     * rule applies to them and none is invented here.
     */
    private static final String FAMILY_ADT = "adt";

    /** One {@code sap-sdk} system-scope dependency. */
    record Bundle(String propertyName, String bundlePrefix, String baselineVersion, String family) {
        Bundle(String propertyName, String bundlePrefix, String baselineVersion) {
            this(propertyName, bundlePrefix, baselineVersion, null);
        }

        String fileNameFor(String version) {
            return bundlePrefix + "_" + version + JAR_SUFFIX;
        }
    }

    /**
     * Must stay in step with the {@code adt.jar.*} properties in
     * {@code apps/openadt-sap-adt/pom.xml}; {@code AdtBundlePomSyncTest} enforces that.
     */
    static final List<Bundle> BUNDLES = List.of(
        new Bundle("adt.jar.communication", "com.sap.adt.communication", ADT_BASELINE, FAMILY_ADT),
        new Bundle("adt.jar.compatibility", "com.sap.adt.compatibility", ADT_BASELINE, FAMILY_ADT),
        new Bundle("adt.jar.destinations", "com.sap.adt.destinations", ADT_BASELINE, FAMILY_ADT),
        new Bundle("adt.jar.destinations.model", "com.sap.adt.destinations.model", ADT_BASELINE, FAMILY_ADT),
        new Bundle("adt.jar.logging", "com.sap.adt.logging", ADT_BASELINE, FAMILY_ADT),
        new Bundle("adt.jar.util", "com.sap.adt.util", ADT_BASELINE, FAMILY_ADT),
        new Bundle("adt.jar.transport", "com.sap.adt.transport", ADT_BASELINE, FAMILY_ADT),
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

    /**
     * Outcome of resolving every bundle against one plugins directory.
     *
     * @param drift bundles resolved to a version other than the tested baseline — reported, not fatal
     * @param missing bundles with no match at all
     * @param incoherent version-set violations: a release family that did not resolve to one version
     */
    public record Resolution(
        Map<String, Path> properties,
        List<String> drift,
        List<String> missing,
        List<String> incoherent
    ) {
        public boolean isComplete() {
            return missing.isEmpty() && incoherent.isEmpty();
        }
    }

    private AdtBundleResolver() {
    }

    public static Resolution resolve(Path pluginsDir) {
        Map<String, Path> properties = new LinkedHashMap<>();
        List<String> drift = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Map<String, Map<String, List<String>>> families = new LinkedHashMap<>();

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
            if (bundle.family() != null) {
                families
                    .computeIfAbsent(bundle.family(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(resolvedVersion, key -> new ArrayList<>())
                    .add(bundle.bundlePrefix());
            }
        }
        return new Resolution(properties, drift, missing, describeIncoherentFamilies(families));
    }

    /**
     * Each bundle is resolved to its own newest version, so a pool holding a partially updated release
     * can otherwise yield a classpath that mixes levels. Any family resolving to more than one version
     * is rejected here rather than left to fail later at compilation or link time.
     */
    private static List<String> describeIncoherentFamilies(Map<String, Map<String, List<String>>> families) {
        List<String> incoherent = new ArrayList<>();
        families.forEach((family, byVersion) -> {
            if (byVersion.size() <= 1) {
                return;
            }
            StringBuilder detail = new StringBuilder(family)
                .append(" bundles resolved to ")
                .append(byVersion.size())
                .append(" different versions:");
            byVersion.forEach((version, prefixes) ->
                detail.append("\n      ").append(version).append(" - ").append(String.join(", ", prefixes)));
            incoherent.add(detail.toString());
        });
        return incoherent;
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
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(candidate -> !versionIn(candidate, prefix).isEmpty())
                .max(Comparator.comparing(
                    candidate -> versionIn(candidate, prefix),
                    OsgiVersions::compare
                ));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /** Version between {@code <prefix>} and {@code .jar}, or empty when the name does not match. */
    private static String versionIn(Path jar, String prefix) {
        String name = jar.getFileName().toString();
        if (!name.startsWith(prefix) || !name.endsWith(JAR_SUFFIX)) {
            return "";
        }
        return name.substring(prefix.length(), name.length() - JAR_SUFFIX.length());
    }

    private static String versionOf(Path jar, String bundlePrefix) {
        return versionIn(jar, bundlePrefix + "_");
    }
}
