package org.openadt.bootstrap;

import org.openadt.config.SystemProfile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NwbcSystemDetector implements SystemDetector {
    private final List<Path> recentsDirectories;

    public NwbcSystemDetector() {
        this(SetupPathLocator.nwbcRecentsDirectories());
    }

    NwbcSystemDetector(List<Path> recentsDirectories) {
        this.recentsDirectories = List.copyOf(recentsDirectories);
    }

    @Override
    public List<SystemProfile> detect() {
        List<SystemProfile> systems = new ArrayList<>();
        for (Path recentsDirectory : recentsDirectories) {
            if (!Files.isDirectory(recentsDirectory)) {
                continue;
            }
            try (var stream = Files.list(recentsDirectory)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".recents"))
                    .forEach(path -> {
                        try {
                            systems.addAll(parseRecentsFile(path));
                        } catch (IOException ignored) {
                            // Best-effort detection only.
                        }
                    });
            } catch (IOException ignored) {
                // Best-effort detection only.
            }
        }
        return systems;
    }

    private List<SystemProfile> parseRecentsFile(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
            NodeList recentNodes = document.getElementsByTagName("recent");
            List<SystemProfile> systems = new ArrayList<>();
            for (int i = 0; i < recentNodes.getLength(); i++) {
                Element recent = (Element) recentNodes.item(i);
                SystemProfile profile = parseRecent(recent);
                if (profile != null) {
                    systems.add(profile);
                }
            }
            return systems;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse NWBC recents file: " + path, e);
        }
    }

    private SystemProfile parseRecent(Element recent) {
        String client = blankToNull(recent.getAttribute("client"));
        String systemId = extractSystemId(recent);
        if (client == null && systemId == null) {
            return null;
        }

        SystemProfile profile = new SystemProfile();
        profile.setSource("sap-business-client");
        profile.setAlias(systemId);
        profile.setSystemId(systemId);
        profile.setClient(client);
        profile.setDescription(blankToNull(recent.getAttribute("connection")));
        return profile;
    }

    private String extractSystemId(Element recent) {
        String url = blankToNull(recent.getAttribute("url"));
        if (url != null) {
            String marker = "~sysid=";
            int index = url.indexOf(marker);
            if (index >= 0) {
                int start = index + marker.length();
                int end = url.indexOf(';', start);
                if (end < 0) {
                    end = url.length();
                }
                return blankToNull(url.substring(start, end));
            }
        }
        String connection = blankToNull(recent.getAttribute("connection"));
        if (connection != null) {
            int start = connection.indexOf('[');
            int end = connection.indexOf(']', start + 1);
            if (start >= 0 && end > start) {
                return blankToNull(connection.substring(start + 1, end));
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
