package org.openadt.sap.adt.destination;

import org.openadt.config.AdtHttpFrontendUrls;
import org.openadt.config.SystemProfile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads {@code saprules.xml} for ADT discovery URLs (shared by bootstrap detector and HTTP MFA).
 */
public final class SapRulesDiscoveryHelper {
    private static final String DEFAULT_ADT_BC_PATH = "/sap/bc/adt";

    private SapRulesDiscoveryHelper() {
    }

    public static List<Path> defaultSapRulesFiles() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String appData = System.getenv("APPDATA");
        if (os.contains("win") && appData != null && !appData.isBlank()) {
            paths.add(Path.of(appData, "SAP", "Common", "saprules.xml"));
        }
        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) {
            paths.add(Path.of("/mnt/c/Users").resolve(Path.of(home).getFileName()).resolve("AppData/Roaming/SAP/Common/saprules.xml"));
        }
        return new ArrayList<>(paths);
    }

    public static List<SystemProfile> detect(List<Path> sapRulesFiles) {
        return detect(sapRulesFiles, DEFAULT_ADT_BC_PATH);
    }

    public static List<SystemProfile> detect(List<Path> sapRulesFiles, String adtBcPath) {
        Map<String, SystemProfile> systemsById = new LinkedHashMap<>();
        for (Path path : sapRulesFiles) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                parseRulesFile(path, systemsById, adtBcPath);
            } catch (IOException ignored) {
                // Best-effort discovery only.
            }
        }
        return new ArrayList<>(systemsById.values());
    }

    public static String adtApiBaseUrlForSystem(String systemId, List<Path> sapRulesFiles) {
        String normalized = systemId == null ? null : systemId.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        for (SystemProfile profile : detect(sapRulesFiles)) {
            if (!normalized.equalsIgnoreCase(profile.getSystemId())) {
                continue;
            }
            if (profile.getAdt() != null) {
                String apiBase = AdtHttpFrontendUrls.resolveAdtApiBase(profile.getAdt());
                if (apiBase != null && !apiBase.isBlank()) {
                    return apiBase;
                }
            }
        }
        return null;
    }

    private static void parseRulesFile(Path path, Map<String, SystemProfile> systemsById, String adtBcPath)
        throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            try {
                factory.setFeature("http://xml.apache.org/features/disallow-doctype-decl", true);
            } catch (ParserConfigurationException ignored) {
                // Xerces-specific feature; not supported by all parsers. The features above still prevent XXE.
            }

            Document document = factory.newDocumentBuilder().parse(inputStream);
            NodeList ruleNodes = document.getElementsByTagName("rule");
            for (int i = 0; i < ruleNodes.getLength(); i++) {
                Element rule = (Element) ruleNodes.item(i);
                mergeProfile(systemsById, parseRule(rule, adtBcPath));
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("XML parser does not support required security feature: " + e.getMessage(), e);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse saprules file: " + path, e);
        }
    }

    private static SystemProfile parseRule(Element rule, String adtBcPath) {
        Element context = firstChild(rule, "context");
        if (context == null) {
            return null;
        }
        String systemId = textOf(context, "system");
        String client = textOf(context, "client");
        String adtHost = extractAdtHost(rule, adtBcPath);
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
        String adtDiscoveryUrl = extractAdtDiscoveryUrl(rule, adtBcPath);
        if (adtDiscoveryUrl != null) {
            if (profile.getAdt() == null) {
                profile.setAdt(new SystemProfile.AdtConfig());
            }
            profile.getAdt().setBaseUrl(AdtHttpFrontendUrls.normalizeToOrigin(adtDiscoveryUrl));
            if (profile.getAdt().getAshost() == null) {
                profile.getAdt().setAshost(extractAdtHost(rule, adtBcPath));
            }
        }
        return profile;
    }

    private static void mergeProfile(Map<String, SystemProfile> systemsById, SystemProfile incoming) {
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
            } else {
                if (existing.getAdt().getAshost() == null) {
                    existing.getAdt().setAshost(incoming.getAdt().getAshost());
                }
                if (existing.getAdt().getBaseUrl() == null && incoming.getAdt().getBaseUrl() != null) {
                    existing.getAdt().setBaseUrl(incoming.getAdt().getBaseUrl());
                }
            }
        }
    }

    private static String extractAdtDiscoveryUrl(Element rule, String adtBcPath) {
        return firstMatchingAdtUri(rule, adtBcPath, uri -> {
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = adtBcPath;
            }
            return uri.getScheme() + "://" + uri.getAuthority() + path;
        });
    }

    private static String extractAdtHost(Element rule, String adtBcPath) {
        return firstMatchingAdtUri(rule, adtBcPath, uri -> blankToNull(uri.getHost()));
    }

    private static String firstMatchingAdtUri(
        Element rule,
        String adtBcPath,
        java.util.function.Function<URI, String> mapper
    ) {
        Element files = firstChild(rule, "files");
        if (files == null) {
            return null;
        }
        NodeList nameNodes = files.getElementsByTagName("name");
        for (int i = 0; i < nameNodes.getLength(); i++) {
            String mapped = mapAdtName(nameNodes.item(i).getTextContent(), adtBcPath, mapper);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    private static String mapAdtName(String rawValue, String adtBcPath, java.util.function.Function<URI, String> mapper) {
        String value = blankToNull(rawValue);
        if (value == null || !value.contains(adtBcPath)) {
            return null;
        }
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

    private static Element firstChild(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private static String textOf(Element parent, String tagName) {
        Element child = firstChild(parent, tagName);
        return child == null ? null : blankToNull(child.getTextContent());
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
