package org.openadt.core;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdtSdkClassLoaderFactory {
    private static final Pattern BUNDLE_VERSION_PATTERN = Pattern.compile("_(.+)\\.jar$");
    private static final List<String> REQUIRED_BUNDLES = List.of(
        "com.sap.adt.communication_",
        "com.sap.adt.compatibility_",
        "com.sap.conn.jco.eclipse_",
        "com.sap.adt.destinations_",
        "com.sap.adt.destinations.model_",
        "com.sap.adt.logging_",
        "com.sap.adt.util_",
        "org.eclipse.core.runtime_",
        "org.eclipse.core.resources_",
        "org.eclipse.core.commands_",
        "org.eclipse.core.databinding_",
        "org.eclipse.core.databinding.beans_",
        "org.eclipse.core.databinding.observable_",
        "org.eclipse.core.databinding.property_",
        "org.eclipse.core.jobs_",
        "org.eclipse.core.net_",
        "org.eclipse.equinox.common_",
        "org.eclipse.equinox.registry_",
        "org.eclipse.osgi_",
        "org.eclipse.osgi.util_",
        "org.osgi.service.prefs_"
    );

    private AdtSdkClassLoaderFactory() {
    }

    public static ClassLoader create(OpenAdtConfig.RuntimeConfig runtime) throws Exception {
        if (runtime == null || runtime.getAdtPluginsDir() == null || runtime.getAdtPluginsDir().isBlank()) {
            throw new IllegalStateException("ADT SDK transport requires runtime.adt_plugins_dir to be configured.");
        }

        Path pluginsDir = Path.of(runtime.getAdtPluginsDir());
        if (!Files.isDirectory(pluginsDir)) {
            throw new IllegalStateException("ADT plugins directory does not exist: " + pluginsDir);
        }

        List<URL> urls = new ArrayList<>();
        for (String prefix : REQUIRED_BUNDLES) {
            urls.add(selectLatestBundle(pluginsDir, prefix).toUri().toURL());
        }

        Path jcoJar = resolveJcoJar(runtime, pluginsDir);
        urls.add(jcoJar.toUri().toURL());

        return new URLClassLoader(urls.toArray(URL[]::new), AdtSdkClassLoaderFactory.class.getClassLoader());
    }

    private static Path resolveJcoJar(OpenAdtConfig.RuntimeConfig runtime, Path pluginsDir) throws Exception {
        Path source;
        if (runtime.getJcoJar() != null && !runtime.getJcoJar().isBlank()) {
            Path configured = Path.of(runtime.getJcoJar());
            if (Files.isRegularFile(configured)) {
                source = configured;
            } else {
                source = selectLatestBundle(pluginsDir, "com.sap.conn.jco_");
            }
        } else {
            source = selectLatestBundle(pluginsDir, "com.sap.conn.jco_");
        }
        return JCoJarCanonicalizer.canonicalize(source);
    }

    private static Path selectLatestBundle(Path pluginsDir, String prefix) {
        try (java.util.stream.Stream<Path> stream = Files.list(pluginsDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .max(AdtSdkClassLoaderFactory::compareBundleVersions)
                .orElseThrow(() -> new IllegalStateException(
                    "Required ADT/Eclipse bundle not found in " + pluginsDir + ": " + prefix + "*.jar"));
        } catch (IOException error) {
            throw new IllegalStateException(
                "Failed to scan ADT/Eclipse bundles in " + pluginsDir + ": " + error.getMessage(),
                error
            );
        }
    }

    private static List<Integer> versionKey(Path path) {
        Matcher matcher = BUNDLE_VERSION_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.find()) {
            return List.of();
        }
        String[] parts = matcher.group(1).split("\\D+");
        List<Integer> key = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) {
                key.add(Integer.parseInt(part));
            }
        }
        return key;
    }

    private static int compareBundleVersions(Path left, Path right) {
        List<Integer> leftKey = versionKey(left);
        List<Integer> rightKey = versionKey(right);
        int max = Math.max(leftKey.size(), rightKey.size());
        for (int i = 0; i < max; i++) {
            int leftPart = i < leftKey.size() ? leftKey.get(i) : 0;
            int rightPart = i < rightKey.size() ? rightKey.get(i) : 0;
            int comparison = Integer.compare(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return left.getFileName().toString().compareTo(right.getFileName().toString());
    }
}
