package org.openadt.bootstrap;

import org.openadt.config.JCoJarCanonicalizer;
import org.openadt.config.OpenAdtConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RuntimeDetector {
    private static final Pattern JCO_JAR_PATTERN = Pattern.compile(
        "(?:com\\.sap\\.conn\\.jco_|jco-)(\\d+(?:\\.\\d+)+)\\.jar"
    );
    private final List<Path> jcoJarRoots;
    private final List<Path> nativeSearchRoots;
    private final List<Path> sapcryptoCandidates;
    private final Path stagedDevcontainerDist;
    private final Path jcoCanonicalCacheDir;

    public RuntimeDetector() {
        this(
            SetupPathLocator.jcoJarRoots(),
            SetupPathLocator.jcoNativeSearchRoots(),
            SetupPathLocator.sapcryptoCandidates()
        );
    }

    RuntimeDetector(List<Path> jcoJarRoots, List<Path> nativeSearchRoots, List<Path> sapcryptoCandidates) {
        this(jcoJarRoots, nativeSearchRoots, sapcryptoCandidates, null);
    }

    RuntimeDetector(
        List<Path> jcoJarRoots,
        List<Path> nativeSearchRoots,
        List<Path> sapcryptoCandidates,
        Path jcoCanonicalCacheDir
    ) {
        this.jcoJarRoots = List.copyOf(jcoJarRoots);
        this.nativeSearchRoots = List.copyOf(nativeSearchRoots);
        this.sapcryptoCandidates = List.copyOf(sapcryptoCandidates);
        this.jcoCanonicalCacheDir = jcoCanonicalCacheDir;
        this.stagedDevcontainerDist =
            Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve(".devcontainer/dist");
    }

    public OpenAdtConfig.RuntimeConfig detect() {
        Optional<Path> jcoJar = findLatestJcoJar();
        Optional<Path> nativeLibrary = findJcoNativeLibrary();
        Optional<Path> sapcrypto = findSapcrypto();
        Optional<Path> adtPluginsDir = findAdtPluginsDir();
        if (jcoJar.isEmpty() && nativeLibrary.isEmpty() && sapcrypto.isEmpty() && adtPluginsDir.isEmpty()) {
            return null;
        }

        OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
        jcoJar
            .map(this::canonicalJcoJar)
            .map(Path::toString)
            .ifPresent(runtime::setJcoJar);
        nativeLibrary.map(Path::getParent).map(Path::toString).ifPresent(runtime::setJcoNativeDir);
        sapcrypto.map(Path::toString).ifPresent(runtime::setSapcrypto);
        adtPluginsDir.map(Path::toString).ifPresent(runtime::setAdtPluginsDir);
        return runtime;
    }

    private Path canonicalJcoJar(Path source) {
        try {
            if (jcoCanonicalCacheDir != null) {
                return JCoJarCanonicalizer.canonicalizeTo(source, jcoCanonicalCacheDir);
            }
            return JCoJarCanonicalizer.canonicalize(source);
        } catch (IOException error) {
            return source;
        }
    }

    private Optional<Path> findLatestJcoJar() {
        Path latest = null;
        for (Path root : jcoJarRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(root)) {
                for (Path candidate : stream.filter(Files::isRegularFile).toList()) {
                    if (!JCO_JAR_PATTERN.matcher(candidate.getFileName().toString()).matches()) {
                        continue;
                    }
                    if (latest == null || compareJcoVersions(candidate, latest) > 0) {
                        latest = candidate;
                    }
                }
            } catch (IOException ignored) {
                // Best-effort discovery only.
            }
        }
        return Optional.ofNullable(latest);
    }

    private List<Integer> jcoVersionKey(Path path) {
        Matcher matcher = JCO_JAR_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return List.of();
        }
        String[] parts = matcher.group(1).split("\\.");
        List<Integer> key = new ArrayList<>(parts.length);
        for (String part : parts) {
            key.add(Integer.parseInt(part));
        }
        return key;
    }

    private int compareJcoVersions(Path left, Path right) {
        List<Integer> leftKey = jcoVersionKey(left);
        List<Integer> rightKey = jcoVersionKey(right);
        int maxParts = Math.max(leftKey.size(), rightKey.size());
        for (int i = 0; i < maxParts; i++) {
            int leftPart = i < leftKey.size() ? leftKey.get(i) : 0;
            int rightPart = i < rightKey.size() ? rightKey.get(i) : 0;
            int comparison = Integer.compare(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private Optional<Path> findJcoNativeLibrary() {
        List<String> names = List.of("sapjco3.dll", "libsapjco3.so", "libsapjco3.dylib");
        Optional<Path> match = findFirstMatch(nativeSearchRoots, names, 8);
        if (match.isPresent()) {
            return match;
        }
        return Optional.empty();
    }

    private Optional<Path> findSapcrypto() {
        for (Path candidate : sapcryptoCandidates) {
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return findFirstMatch(
            nativeSearchRoots,
            List.of("sapcrypto.dll", "libsapcrypto.so", "libsapcrypto.dylib"),
            6
        );
    }

    private Optional<Path> findAdtPluginsDir() {
        for (Path root : jcoJarRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            if (hasRequiredAdtBundles(root)) {
                return Optional.of(root);
            }
        }
        return Optional.empty();
    }

    private boolean hasRequiredAdtBundles(Path pluginsDir) {
        return hasBundle(pluginsDir, "com.sap.adt.communication_")
            && hasBundle(pluginsDir, "com.sap.adt.destinations_")
            && hasBundle(pluginsDir, "com.sap.adt.destinations.model_")
            && hasBundle(pluginsDir, "com.sap.adt.compatibility_")
            && hasBundle(pluginsDir, "com.sap.adt.logging_")
            && hasBundle(pluginsDir, "com.sap.adt.util_");
    }

    private boolean hasBundle(Path pluginsDir, String prefix) {
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            return stream.anyMatch(path -> Files.isRegularFile(path)
                && path.getFileName().toString().startsWith(prefix)
                && path.getFileName().toString().endsWith(".jar"));
        } catch (IOException ignored) {
            return false;
        }
    }

    private Optional<Path> findFirstMatch(List<Path> roots, List<String> fileNames, int maxDepth) {
        List<String> normalizedNames = fileNames.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .toList();

        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try {
                Optional<Path> match = findFirstMatchUnderRoot(root, normalizedNames, maxDepth);
                if (match.isPresent()) {
                    return match;
                }
            } catch (IOException ignored) {
                // Best-effort discovery only.
            }
        }
        return Optional.empty();
    }

    private Optional<Path> findFirstMatchUnderRoot(Path root, List<String> normalizedNames, int maxDepth)
        throws IOException {
        Path[] match = new Path[1];
        Files.walkFileTree(root, java.util.Set.of(), maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path normalized = dir.toAbsolutePath().normalize();
                if (!normalized.equals(root.toAbsolutePath().normalize())
                    && normalized.startsWith(stagedDevcontainerDist)
                    && !root.toAbsolutePath().normalize().startsWith(stagedDevcontainerDist)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()
                    && normalizedNames.contains(file.getFileName().toString().toLowerCase(Locale.ROOT))) {
                    match[0] = file;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.SKIP_SUBTREE;
            }
        });
        return Optional.ofNullable(match[0]);
    }
}
