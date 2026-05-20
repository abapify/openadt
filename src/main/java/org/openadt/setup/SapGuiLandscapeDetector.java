package org.openadt.setup;

import org.openadt.core.SystemProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SapGuiLandscapeDetector implements SystemDetector {
    @Override
    public List<SystemProfile> detect() {
        List<SystemProfile> systems = new ArrayList<>();
        List<Path> landscapePaths = getLandscapePaths();
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

    private List<Path> getLandscapePaths() {
        List<Path> paths = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", "");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                paths.add(Path.of(appData, "SAP", "Common", "SAPUILandscape.xml"));
            }
            paths.add(Path.of(home, "AppData", "Roaming", "SAP", "Common", "SAPUILandscape.xml"));
        } else if (os.contains("mac")) {
            paths.add(Path.of(home, "Library", "Application Support", "SAP", "Common", "SAPUILandscape.xml"));
        }
        return paths;
    }

    private List<SystemProfile> parseLandscapeFile(Path path) throws IOException {
        List<SystemProfile> systems = new ArrayList<>();
        String content = Files.readString(path);
        // Simple XML parsing for system entries
        int idx = 0;
        while ((idx = content.indexOf("<System ", idx)) >= 0) {
            int end = content.indexOf("/>", idx);
            if (end < 0) break;
            String entry = content.substring(idx, end + 2);
            SystemProfile profile = parseSystemEntry(entry);
            if (profile != null) {
                profile.setSource("sapgui");
                systems.add(profile);
            }
            idx = end + 2;
        }
        return systems;
    }

    private SystemProfile parseSystemEntry(String entry) {
        SystemProfile profile = new SystemProfile();
        String name = extractAttr(entry, "name");
        String server = extractAttr(entry, "server");
        String systemid = extractAttr(entry, "systemid");
        String sysno = extractAttr(entry, "sysno");

        if (name == null && server == null) return null;

        profile.setAlias(name);
        profile.setDescription(name);
        profile.setSystemId(systemid);

        SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
        if (server != null) jco.setAshost(server);
        if (sysno != null) jco.setSysnr(sysno);
        profile.setJco(jco);

        return profile;
    }

    private String extractAttr(String xml, String attr) {
        String search = attr + "=\"";
        int start = xml.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = xml.indexOf('"', start);
        if (end < 0) return null;
        return xml.substring(start, end);
    }
}
