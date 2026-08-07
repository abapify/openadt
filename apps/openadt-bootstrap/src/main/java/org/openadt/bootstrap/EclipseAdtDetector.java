package org.openadt.bootstrap;

import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.destination.EclipseDestinationLocator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects SAP systems from ADT destinations that Eclipse persists in its workspace semantic cache
 * ({@code .metadata/.../semantic/.cache/<id>/.destination.properties}).
 *
 * <p>Parsing is delegated to {@link EclipseDestinationLocator} so setup and {@code fetch}/{@code proxy}
 * resolve destinations through exactly one code path.
 */
public class EclipseAdtDetector implements SystemDetector {
    private static final String SOURCE = "eclipse-adt";

    private final EclipseDestinationLocator locator;

    public EclipseAdtDetector() {
        this(new EclipseDestinationLocator());
    }

    EclipseAdtDetector(List<Path> workspacePaths) {
        this(new EclipseDestinationLocator(workspacePaths));
    }

    EclipseAdtDetector(EclipseDestinationLocator locator) {
        this.locator = locator;
    }

    @Override
    public List<SystemProfile> detect() {
        List<EclipseDestinationLocator.EclipseDestinationEntry> entries;
        try {
            entries = locator.listAll();
        } catch (IOException unreadable) {
            // Best-effort discovery only; an unreadable workspace must not fail setup.
            return List.of();
        }

        List<SystemProfile> systems = new ArrayList<>(entries.size());
        for (EclipseDestinationLocator.EclipseDestinationEntry entry : entries) {
            SystemProfile profile = EclipseDestinationLocator.toSystemProfile(entry);
            profile.setSource(SOURCE);
            if (profile.getDescription() == null || profile.getDescription().isBlank()) {
                profile.setDescription(describe(entry));
            }
            systems.add(profile);
        }
        return systems;
    }

    private static String describe(EclipseDestinationLocator.EclipseDestinationEntry entry) {
        String systemId = entry.systemId();
        if (systemId == null || systemId.isBlank()) {
            return "Eclipse ADT connection";
        }
        String client = entry.client();
        if (client == null || client.isBlank()) {
            return "Eclipse ADT connection " + systemId;
        }
        return "Eclipse ADT connection " + systemId + "/" + client;
    }
}
