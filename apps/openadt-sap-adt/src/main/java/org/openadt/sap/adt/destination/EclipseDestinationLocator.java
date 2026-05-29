package org.openadt.sap.adt.destination;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import org.openadt.config.SystemProfile;

import java.util.stream.Stream;

/**
 * Finds SAP ADT destinations persisted by Eclipse in workspace semantic cache
 * ({@code .metadata/.../semantic/.cache/<id>/.destination.properties}).
 */
public final class EclipseDestinationLocator {
    public static final String DESTINATION_FILE = ".destination.properties";

    private final List<Path> workspaceRoots;

    public EclipseDestinationLocator() {
        this(discoverWorkspaceRoots());
    }

    public EclipseDestinationLocator(List<Path> workspaceRoots) {
        this.workspaceRoots = List.copyOf(workspaceRoots);
    }

    public static EclipseDestinationLocator forWorkspace(Path workspaceRoot) {
        return new EclipseDestinationLocator(List.of(workspaceRoot));
    }

    public List<EclipseDestinationEntry> listAll() throws IOException {
        List<EclipseDestinationEntry> entries = new ArrayList<>();
        for (Path workspace : workspaceRoots) {
            Path cacheRoot = semanticCacheRoot(workspace);
            if (!Files.isDirectory(cacheRoot)) {
                continue;
            }
            try (Stream<Path> projects = Files.list(cacheRoot)) {
                projects
                    .filter(Files::isDirectory)
                    .forEach(projectDir -> {
                        Path destinationFile = projectDir.resolve(DESTINATION_FILE);
                        if (!Files.isRegularFile(destinationFile)) {
                            return;
                        }
                        try {
                            entries.add(loadEntry(workspace, destinationFile));
                        } catch (IOException ignored) {
                            // Skip unreadable destination files.
                        }
                    });
            }
        }
        entries.sort(Comparator.comparing(EclipseDestinationEntry::id, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public Optional<EclipseDestinationEntry> find(String query) throws IOException {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalized = query.trim();
        List<EclipseDestinationEntry> matches = new ArrayList<>();
        for (EclipseDestinationEntry entry : listAll()) {
            if (normalized.equalsIgnoreCase(entry.id())
                || normalized.equalsIgnoreCase(entry.systemId())
                || entry.id().toLowerCase(Locale.ROOT).startsWith(normalized.toLowerCase(Locale.ROOT) + "_")) {
                matches.add(entry);
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }
        matches.sort(Comparator
            .comparing((EclipseDestinationEntry e) -> !normalized.equalsIgnoreCase(e.id()))
            .thenComparing(EclipseDestinationEntry::id, String.CASE_INSENSITIVE_ORDER));
        return Optional.of(matches.get(0));
    }

    private static EclipseDestinationEntry loadEntry(Path workspace, Path destinationFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(destinationFile)) {
            properties.load(input);
        }
        String id = properties.getProperty("id", destinationFile.getParent().getFileName().toString());
        return new EclipseDestinationEntry(
            workspace,
            destinationFile,
            id,
            properties.getProperty("systemId"),
            properties.getProperty("client"),
            properties.getProperty("user"),
            properties.getProperty("language"),
            properties.getProperty("messageServer"),
            properties.getProperty("messageServerService"),
            properties.getProperty("group"),
            properties.getProperty("partnerName"),
            properties.getProperty("SSOEnabled"),
            properties.getProperty("SNCType")
        );
    }

    public static SystemProfile toSystemProfile(EclipseDestinationEntry entry) {
        SystemProfile profile = new SystemProfile();
        profile.setAlias(entry.id());
        profile.setSystemId(entry.systemId());
        profile.setClient(entry.client());
        profile.setUser(entry.user());
        profile.setLanguage(entry.language());
        SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
        jco.setMshost(entry.messageServer());
        jco.setMsserv(entry.messageServerService());
        jco.setR3name(entry.systemId());
        jco.setGroup(entry.group());
        jco.setSncPartnername(entry.partnerName());
        jco.setSncMode("1");
        jco.setSncSso("0".equals(entry.ssoEnabled()) ? "0" : "1");
        if (entry.sncType() != null && !entry.sncType().isBlank()) {
            jco.setSncQop(entry.sncType());
        }
        profile.setJco(jco);
        return profile;
    }

    static Path semanticCacheRoot(Path workspace) {
        return workspace.resolve(".metadata/.plugins/org.eclipse.core.resources.semantic/.cache");
    }

    static List<Path> discoverWorkspaceRoots() {
        return EclipseWorkspacePaths.discoverWorkspaceRoots();
    }

    public record EclipseDestinationEntry(
        Path workspace,
        Path destinationFile,
        String id,
        String systemId,
        String client,
        String user,
        String language,
        String messageServer,
        String messageServerService,
        String group,
        String partnerName,
        String ssoEnabled,
        String sncType
    ) {
    }
}
