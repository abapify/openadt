package org.openadt.setup;

import org.openadt.core.SystemProfile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SapRulesDetector implements SystemDetector {
    private static final String DEFAULT_ADT_BC_PATH = "/sap/bc/adt";

    private final List<Path> sapRulesFiles;
    private final String adtBcPath;

    public SapRulesDetector() {
        this(SetupPathLocator.sapRulesFiles());
    }

    SapRulesDetector(List<Path> sapRulesFiles) {
        this(sapRulesFiles, DEFAULT_ADT_BC_PATH);
    }

    SapRulesDetector(List<Path> sapRulesFiles, String adtBcPath) {
        this.sapRulesFiles = List.copyOf(sapRulesFiles);
        this.adtBcPath = adtBcPath;
    }

    @Override
    public List<SystemProfile> detect() {
        Map<String, SystemProfile> systemsById = new LinkedHashMap<>();
        for (Path path : sapRulesFiles) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                parseRulesFile(path, systemsById);
            } catch (IOException ignored) {
                // Best-effort discovery only.
            }
        }
        return new ArrayList<>(systemsById.values());
    }

    private void parseRulesFile(Path path, Map<String, SystemProfile> systemsById) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
            NodeList ruleNodes = document.getElementsByTagName("rule");
            for (int i = 0; i < ruleNodes.getLength(); i++) {
                Element rule = (Element) ruleNodes.item(i);
                mergeProfile(systemsById, parseRule(rule));
            }
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse saprules file: " + path, e);
        }
    }

    private SystemProfile parseRule(Element rule) {
        Element context = firstChild(rule, "context");
        if (context == null) {
            return null;
        }
        String systemId = textOf(context, "system");
        String client = textOf(context, "client");
        String adtHost = extractAdtHost(rule);
        if (systemId == null && client == null && adtHost == null) {
            return null;
        }

        SystemProfile profile = new SystemProfile();
        profile.setSource("saprules");
        profile.setAlias(systemId);
        profile.setSystemId(systemId);
        profile.setClient(client);

        if (adtHost != null) {
            SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
            adt.setAshost(adtHost);
            profile.setAdt(adt);
        }
        String adtDiscoveryUrl = extractAdtDiscoveryUrl(rule);
        if (adtDiscoveryUrl != null) {
            if (profile.getAdt() == null) {
                profile.setAdt(new SystemProfile.AdtConfig());
            }
            profile.getAdt().setDiscoveryUrl(adtDiscoveryUrl);
            if (profile.getAdt().getAshost() == null) {
                profile.getAdt().setAshost(extractAdtHost(rule));
            }
        }
        return profile;
    }

    private void mergeProfile(Map<String, SystemProfile> systemsById, SystemProfile incoming) {
        if (incoming == null || incoming.getSystemId() == null) {
            return;
        }
        SystemProfile existing = systemsById.get(incoming.getSystemId());
        if (existing == null) {
            systemsById.put(incoming.getSystemId(), incoming);
            return;
        }
        if (existing.getClient() == null) {
            existing.setClient(incoming.getClient());
        }
        if (incoming.getAdt() != null) {
            if (existing.getAdt() == null) {
                existing.setAdt(incoming.getAdt());
            } else if (existing.getAdt().getAshost() == null) {
                existing.getAdt().setAshost(incoming.getAdt().getAshost());
            }
            if (existing.getAdt().getDiscoveryUrl() == null && incoming.getAdt().getDiscoveryUrl() != null) {
                existing.getAdt().setDiscoveryUrl(incoming.getAdt().getDiscoveryUrl());
            }
        }
    }

    private String extractAdtDiscoveryUrl(Element rule) {
        return firstMatchingAdtUri(rule, this::toDiscoveryUrl);
    }

    private String extractAdtHost(Element rule) {
        return firstMatchingAdtUri(rule, uri -> blankToNull(uri.getHost()));
    }

    private String firstMatchingAdtUri(Element rule, java.util.function.Function<URI, String> mapper) {
        Element files = firstChild(rule, "files");
        if (files == null) {
            return null;
        }
        NodeList nameNodes = files.getElementsByTagName("name");
        for (int i = 0; i < nameNodes.getLength(); i++) {
            String mapped = mapAdtName(nameNodes.item(i).getTextContent(), mapper);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    private String mapAdtName(String rawValue, java.util.function.Function<URI, String> mapper) {
        String value = blankToNull(rawValue);
        if (value == null || !value.contains(adtBcPath)) {
            return null;
        }
        return mapAdtUri(value, mapper);
    }

    private String mapAdtUri(String value, java.util.function.Function<URI, String> mapper) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null) {
                return null;
            }
            return mapper.apply(uri);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String toDiscoveryUrl(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            path = adtBcPath;
        }
        return uri.getScheme() + "://" + uri.getAuthority() + path;
    }

    private Element firstChild(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private String textOf(Element parent, String tagName) {
        Element child = firstChild(parent, tagName);
        return child == null ? null : blankToNull(child.getTextContent());
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
