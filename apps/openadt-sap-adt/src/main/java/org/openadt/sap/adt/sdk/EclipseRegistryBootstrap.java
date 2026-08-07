package org.openadt.sap.adt.sdk;

import org.eclipse.core.runtime.ContributorFactorySimple;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.RegistryFactory;
import org.eclipse.core.runtime.spi.RegistryStrategy;
import org.openadt.config.CliLog;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Gives the ADT SDK an Eclipse extension registry when running outside Eclipse.
 *
 * <p>Parts of the SDK dereference the registry without a null check. For example
 * {@code AdtLogonService.findExtensions()} is effectively:
 *
 * <pre>
 * Platform.getExtensionRegistry()
 *     .getExtensionPoint("com.sap.adt.destinations.logonListeners")
 *     .getExtensions();
 * </pre>
 *
 * <p>With no OSGi framework running, {@code getExtensionRegistry()} returns {@code null} and logon
 * fails with a {@link NullPointerException}. An <em>empty</em> registry is not enough either:
 * {@code getExtensionPoint} returns {@code null} for an unknown point, so the same call still fails
 * one link further along. The extension points therefore have to be really declared.
 *
 * <p>So this installs a standalone registry and declares the extension <em>points</em> of every SAP
 * bundle on the classpath, read from their {@code plugin.xml}.
 *
 * <p>The {@code <extension>} contributions are deliberately dropped. Registering them makes the SDK
 * activate optional collaborators that only work inside a full Eclipse — notably
 * {@code LoggingCommunicationListener}, which reads a preference and so reaches
 * {@code ConfigurationScope.getLocation()}. That needs an OSGi configuration {@code Location} service
 * which does not exist outside a framework, and fails with a {@link NullPointerException} during
 * class initialization. Declaring the points alone is exactly what the null-unsafe lookups need: they
 * resolve to a point with zero extensions, and the SDK proceeds without pulling in Eclipse-only
 * machinery.
 */
public final class EclipseRegistryBootstrap {
    private static final String PLUGIN_XML = "plugin.xml";
    private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";
    private static final String SAP_BUNDLE_PREFIX = "com.sap.";

    private static volatile boolean prepared;

    private EclipseRegistryBootstrap() {
    }

    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        if (RegistryFactory.getRegistry() != null) {
            // Running inside a real OSGi framework, or already bootstrapped.
            prepared = true;
            return;
        }
        try {
            Object token = new Object();
            IExtensionRegistry registry = RegistryFactory.createRegistry(
                new RegistryStrategy(null, null),
                token,
                token
            );
            RegistryFactory.setDefaultRegistryProvider(() -> registry);
            int contributed = contributeSapBundles(registry, token);
            prepared = true;
            CliLog.sdk("eclipse extension registry ready (" + contributed + " SAP bundles contributed)");
        } catch (Exception error) {
            throw new IllegalStateException(
                "Failed to initialize Eclipse extension registry: " + error.getMessage(),
                error
            );
        }
    }

    /**
     * Declares the extension points of each SAP bundle on the classpath. Bundles are deduplicated by
     * symbolic name, first classpath entry winning, because a p2 pool commonly holds several versions
     * of the same bundle and the registry rejects a contributor twice.
     */
    private static int contributeSapBundles(IExtensionRegistry registry, Object token) {
        Set<String> seen = new LinkedHashSet<>();
        int contributed = 0;
        for (Path entry : classpathJars()) {
            try (JarFile jar = new JarFile(entry.toFile())) {
                String symbolicName = symbolicNameOf(jar);
                if (symbolicName == null
                    || !symbolicName.startsWith(SAP_BUNDLE_PREFIX)
                    || !seen.add(symbolicName)) {
                    continue;
                }
                if (contribute(registry, token, jar, symbolicName)) {
                    contributed++;
                }
            } catch (IOException unreadable) {
                // A jar we cannot open simply contributes nothing.
                CliLog.sdk("skipped unreadable classpath entry " + entry);
            }
        }
        return contributed;
    }

    private static boolean contribute(
        IExtensionRegistry registry,
        Object token,
        JarFile jar,
        String symbolicName
    ) throws IOException {
        var pluginXml = jar.getEntry(PLUGIN_XML);
        if (pluginXml == null) {
            return false;
        }
        byte[] pointsOnly;
        try (InputStream contents = jar.getInputStream(pluginXml)) {
            pointsOnly = PluginXmlExtensionPoints.rewrite(contents);
        } catch (RuntimeException | IOException unparseable) {
            CliLog.sdk("skipped unparseable plugin.xml of " + symbolicName + ": " + unparseable.getMessage());
            return false;
        }
        if (pointsOnly.length == 0) {
            return false;
        }
        IContributor contributor = ContributorFactorySimple.createContributor(symbolicName);
        try (InputStream contents = new ByteArrayInputStream(pointsOnly)) {
            return registry.addContribution(contents, contributor, false, symbolicName, null, token);
        } catch (RuntimeException rejected) {
            // One conflicting contribution must not stop the rest.
            CliLog.sdk("skipped plugin.xml of " + symbolicName + ": " + rejected.getMessage());
            return false;
        }
    }

    private static String symbolicNameOf(JarFile jar) throws IOException {
        Manifest manifest = jar.getManifest();
        if (manifest == null) {
            return null;
        }
        String value = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLIC_NAME);
        if (value == null || value.isBlank()) {
            return null;
        }
        // Strip directives such as ";singleton:=true".
        int directive = value.indexOf(';');
        return (directive < 0 ? value : value.substring(0, directive)).trim();
    }

    private static List<Path> classpathJars() {
        List<Path> jars = new ArrayList<>();
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(File.pathSeparator)) {
            if (entry.isBlank() || !entry.endsWith(".jar")) {
                continue;
            }
            Path path = Path.of(entry);
            if (Files.isRegularFile(path)) {
                jars.add(path);
            }
        }
        return jars;
    }
}
