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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SapGuiLandscapeDetector implements SystemDetector {
    private final List<Path> landscapePaths;

    public SapGuiLandscapeDetector() {
        List<Path> defaultPaths = new ArrayList<>(SetupPathLocator.sapGuiLandscapeFiles());
        defaultPaths.addAll(SetupPathLocator.logonServerCacheFiles());
        this.landscapePaths = List.copyOf(defaultPaths);
    }

    SapGuiLandscapeDetector(List<Path> landscapePaths) {
        this.landscapePaths = List.copyOf(landscapePaths);
    }

    @Override
    public List<SystemProfile> detect() {
        List<SystemProfile> systems = new ArrayList<>();
        for (Path path : landscapePaths) {
            if (Files.exists(path)) {
                try {
                    systems.addAll(parseLandscapeFile(path));
                } catch (IOException e) {
                    // Skip unreadable files
                }
            }
        }
        return systems;
    }

    private List<SystemProfile> parseLandscapeFile(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
            List<SystemProfile> systems = new ArrayList<>();
            parseClassicSystems(document, systems);
            parseLoadBalancedSystems(document, systems);
            return systems;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse SAP GUI landscape: " + path, e);
        }
    }

    private void parseClassicSystems(Document document, List<SystemProfile> systems) {
        NodeList systemNodes = document.getElementsByTagName("System");
        for (int i = 0; i < systemNodes.getLength(); i++) {
            SystemProfile profile = parseSystemEntry((Element) systemNodes.item(i));
            if (profile != null) {
                systems.add(profile);
            }
        }
    }

    private void parseLoadBalancedSystems(Document document, List<SystemProfile> systems) {
        Map<String, Element> messageServersById = new LinkedHashMap<>();
        NodeList messageServerNodes = document.getElementsByTagName("Messageserver");
        for (int i = 0; i < messageServerNodes.getLength(); i++) {
            Element messageServer = (Element) messageServerNodes.item(i);
            String uuid = blankToNull(messageServer.getAttribute("uuid"));
            if (uuid != null) {
                messageServersById.put(uuid, messageServer);
            }
        }

        NodeList serviceNodes = document.getElementsByTagName("Service");
        for (int i = 0; i < serviceNodes.getLength(); i++) {
            SystemProfile profile = parseServiceEntry((Element) serviceNodes.item(i), messageServersById);
            if (profile != null) {
                systems.add(profile);
            }
        }
    }

    private SystemProfile parseSystemEntry(Element entry) {
        SystemProfile profile = new SystemProfile();
        String name = blankToNull(entry.getAttribute("name"));
        String server = blankToNull(entry.getAttribute("server"));
        String systemid = blankToNull(entry.getAttribute("systemid"));
        String sysno = blankToNull(entry.getAttribute("sysno"));

        if (name == null && server == null) return null;

        profile.setAlias(name);
        profile.setDescription(name);
        profile.setSystemId(systemid);

        SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
        if (server != null) jco.setAshost(server);
        if (sysno != null) jco.setSysnr(sysno);
        profile.setJco(jco);
        profile.setSource("sapgui");

        return profile;
    }

    private SystemProfile parseServiceEntry(Element service, Map<String, Element> messageServersById) {
        String type = blankToNull(service.getAttribute("type"));
        if (type != null && !"SAPGUI".equalsIgnoreCase(type)) {
            return null;
        }

        String systemId = blankToNull(service.getAttribute("systemid"));
        String messageServerId = blankToNull(service.getAttribute("msid"));
        Element messageServer = messageServerId != null ? messageServersById.get(messageServerId) : null;

        if (systemId == null && messageServer == null) {
            return null;
        }

        SystemProfile profile = new SystemProfile();
        profile.setAlias(firstNonBlank(systemId, blankToNull(service.getAttribute("name"))));
        profile.setSystemId(firstNonBlank(systemId, messageServer != null ? blankToNull(messageServer.getAttribute("name")) : null));
        profile.setDescription(firstNonBlank(
            messageServer != null ? blankToNull(messageServer.getAttribute("description")) : null,
            blankToNull(service.getAttribute("name")),
            profile.getAlias()
        ));
        profile.setSource("sapgui");

        SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
        if (messageServer != null) {
            jco.setMshost(blankToNull(messageServer.getAttribute("host")));
            jco.setMsserv(blankToNull(messageServer.getAttribute("port")));
        }
        jco.setR3name(profile.getSystemId());
        jco.setGroup(blankToNull(service.getAttribute("server")));

        String sncName = blankToNull(service.getAttribute("sncname"));
        if (sncName != null) {
            jco.setSncMode("1");
            jco.setSncPartnername(sncName);
            jco.setSncSso("1");
        }
        String sncQop = blankToNull(service.getAttribute("sncop"));
        if (sncQop != null) {
            jco.setSncQop(sncQop);
        }

        profile.setJco(jco);
        return profile;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
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
