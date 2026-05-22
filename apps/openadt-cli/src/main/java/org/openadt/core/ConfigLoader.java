package org.openadt.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class ConfigLoader {
    private final TomlMapper mapper;
    private final Path workingDirectory;
    private final Path homeDirectory;
    private final Path environmentConfigPath;

    public ConfigLoader() {
        this(
            Path.of(System.getProperty("user.dir", ".")),
            Path.of(System.getProperty("user.home")),
            normalizeEnvPath(System.getenv("OPENADT_CONFIG"))
        );
    }

    ConfigLoader(Path workingDirectory, Path homeDirectory) {
        this(workingDirectory, homeDirectory, null);
    }

    ConfigLoader(Path workingDirectory, Path homeDirectory, Path environmentConfigPath) {
        this.mapper = new TomlMapper();
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        this.homeDirectory = homeDirectory.toAbsolutePath().normalize();
        this.environmentConfigPath = environmentConfigPath != null ? environmentConfigPath.toAbsolutePath().normalize() : null;
    }

    public Path getDefaultConfigPath() {
        if (environmentConfigPath != null) {
            return environmentConfigPath;
        }
        for (Path candidate : configSearchPaths()) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return localConfigPath();
    }

    public Path getDefaultSetupConfigPath() {
        return globalConfigPath();
    }

    public OpenAdtConfig load() throws IOException {
        if (environmentConfigPath != null) {
            return load(environmentConfigPath);
        }
        for (Path candidate : configSearchPaths()) {
            if (Files.exists(candidate)) {
                return load(candidate);
            }
        }
        return new OpenAdtConfig();
    }

    public OpenAdtConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new OpenAdtConfig();
        }
        return loadFragment(path.toAbsolutePath().normalize(), new LinkedHashSet<>());
    }

    public void save(OpenAdtConfig config, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        mapper.writeValue(path.toFile(), config);
    }

    public void saveSetupConfig(OpenAdtConfig config, Path entrypointPath) throws IOException {
        Path normalizedEntrypoint = entrypointPath.toAbsolutePath().normalize();
        Path rootDir = normalizedEntrypoint.getParent();
        Path destinationsDir = rootDir.resolve("destinations");
        Path destinationsFile = destinationsDir.resolve("detected.openadt.toml");
        Path localFile = rootDir.resolve("local.openadt.toml");

        Files.createDirectories(destinationsDir);
        writeEntrypoint(normalizedEntrypoint, List.of("destinations/*.openadt.toml", "local.openadt.toml"));
        writeDestinationsFragment(destinationsFile, config.getSystems());
        writeLocalFragment(localFile, config);
    }

    private OpenAdtConfig loadFragment(Path path, Set<Path> visited) throws IOException {
        if (!visited.add(path)) {
            throw new IOException("Config include cycle detected at " + path);
        }

        ConfigFragment fragment = mapper.readValue(path.toFile(), ConfigFragment.class);
        OpenAdtConfig config = toConfig(fragment);

        if (fragment.merge != null && fragment.merge.includes != null && !fragment.merge.includes.isEmpty()) {
            OpenAdtConfig merged = new OpenAdtConfig();
            for (Path included : expandIncludes(path.getParent(), fragment.merge.includes)) {
                mergeInto(merged, loadFragment(included, visited));
            }
            if (isLastWins(fragment.merge)) {
                mergeIntoLastWins(merged, config);
            } else {
                mergeInto(merged, config);
            }
            visited.remove(path);
            return merged;
        }

        visited.remove(path);
        return config;
    }

    private List<Path> expandIncludes(Path baseDir, List<String> includes) throws IOException {
        List<Path> expanded = new ArrayList<>();
        for (String include : includes) {
            expanded.addAll(expandInclude(baseDir, include));
        }
        return expanded;
    }

    private List<Path> expandInclude(Path baseDir, String include) throws IOException {
        String pattern = resolveIncludePattern(baseDir, include);
        if (!containsGlob(pattern)) {
            Path resolved = Path.of(pattern);
            return Files.exists(resolved) ? List.of(resolved.toAbsolutePath().normalize()) : List.of();
        }

        Path searchRoot = findSearchRoot(pattern);
        if (!Files.exists(searchRoot)) {
            return List.of();
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            return stream
                .filter(Files::isRegularFile)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(matcher::matches)
                .sorted()
                .toList();
        }
    }

    private String resolveIncludePattern(Path baseDir, String include) {
        String value = include;
        if (include.startsWith("~/") || include.startsWith("~\\")) {
            value = homeDirectory.resolve(include.substring(2)).toString();
        }
        if (!containsGlob(value)) {
            Path raw = Path.of(value);
            if (raw.isAbsolute()) {
                return raw.toAbsolutePath().normalize().toString();
            }
            return baseDir.resolve(raw).toAbsolutePath().normalize().toString();
        }

        int wildcardIndex = firstWildcardIndex(value);
        int separatorIndex = Math.max(value.lastIndexOf('/', wildcardIndex), value.lastIndexOf('\\', wildcardIndex));
        String directoryPart = separatorIndex >= 0 ? value.substring(0, separatorIndex) : "";
        String suffix = separatorIndex >= 0 ? value.substring(separatorIndex + 1) : value;

        Path resolvedDir;
        if (directoryPart.isEmpty()) {
            resolvedDir = baseDir.toAbsolutePath().normalize();
        } else {
            Path rawDir = Path.of(directoryPart);
            resolvedDir = rawDir.isAbsolute()
                ? rawDir.toAbsolutePath().normalize()
                : baseDir.resolve(rawDir).toAbsolutePath().normalize();
        }

        // PathMatcher glob on Windows treats '\' as escape; use '/' in glob patterns.
        String normalizedSuffix = suffix.replace("\\", "/");
        return resolvedDir.toString().replace("\\", "/") + "/" + normalizedSuffix;
    }

    private boolean containsGlob(String pattern) {
        return pattern.contains("*") || pattern.contains("?") || pattern.contains("[") || pattern.contains("{");
    }

    private Path findSearchRoot(String pattern) {
        int wildcardIndex = firstWildcardIndex(pattern);
        int separatorIndex = Math.max(pattern.lastIndexOf('/', wildcardIndex), pattern.lastIndexOf('\\', wildcardIndex));
        if (separatorIndex < 0) {
            return workingDirectory;
        }
        String prefix = pattern.substring(0, separatorIndex);
        if (prefix.isEmpty()) {
            return Path.of(FileSystems.getDefault().getSeparator());
        }
        return Path.of(prefix);
    }

    private int firstWildcardIndex(String pattern) {
        int result = pattern.length();
        for (char wildcard : new char[]{'*', '?', '[', '{'}) {
            int index = pattern.indexOf(wildcard);
            if (index >= 0 && index < result) {
                result = index;
            }
        }
        return result;
    }

    private OpenAdtConfig toConfig(ConfigFragment fragment) {
        OpenAdtConfig config = new OpenAdtConfig();
        config.setVersion(fragment.version);
        config.setRuntime(fragment.runtime);
        config.setSecureLogin(fragment.secureLogin);
        config.setProxy(fragment.proxy);

        LinkedHashMap<String, SystemProfile> systems = new LinkedHashMap<>();
        if (fragment.systems != null) {
            for (SystemProfile system : fragment.systems) {
                mergeSystem(systems, system);
            }
        }
        if (fragment.destinations != null) {
            for (Map.Entry<String, SystemProfile> entry : fragment.destinations.entrySet()) {
                SystemProfile system = entry.getValue();
                if (system == null) {
                    system = new SystemProfile();
                }
                if (system.getAlias() == null) {
                    system.setAlias(entry.getKey());
                }
                mergeSystem(systems, system);
            }
        }
        if (!systems.isEmpty()) {
            config.setSystems(new ArrayList<>(systems.values()));
        }
        return config;
    }

    private boolean isLastWins(MergeConfig merge) {
        return merge.strategy == null
            || merge.strategy.isBlank()
            || "last-wins".equalsIgnoreCase(merge.strategy);
    }

    private void mergeIntoLastWins(OpenAdtConfig target, OpenAdtConfig source) {
        if (source.getVersion() > 0) {
            target.setVersion(source.getVersion());
        }
        mergeRuntimeLastWins(target, source.getRuntime());
        mergeSecureLoginLastWins(target, source.getSecureLogin());
        mergeProxyLastWins(target, source.getProxy());

        LinkedHashMap<String, SystemProfile> mergedSystems = new LinkedHashMap<>();
        if (target.getSystems() != null) {
            for (SystemProfile system : target.getSystems()) {
                mergeSystem(mergedSystems, system);
            }
        }
        if (source.getSystems() != null) {
            for (SystemProfile system : source.getSystems()) {
                mergeSystemLastWins(mergedSystems, system);
            }
        }
        if (!mergedSystems.isEmpty()) {
            target.setSystems(new ArrayList<>(mergedSystems.values()));
        }
    }

    private void mergeInto(OpenAdtConfig target, OpenAdtConfig source) {
        if (source.getVersion() > 0) {
            target.setVersion(source.getVersion());
        }
        mergeRuntime(target, source.getRuntime());
        mergeSecureLogin(target, source.getSecureLogin());
        mergeProxy(target, source.getProxy());

        LinkedHashMap<String, SystemProfile> mergedSystems = new LinkedHashMap<>();
        if (target.getSystems() != null) {
            for (SystemProfile system : target.getSystems()) {
                mergeSystem(mergedSystems, system);
            }
        }
        if (source.getSystems() != null) {
            for (SystemProfile system : source.getSystems()) {
                mergeSystem(mergedSystems, system);
            }
        }
        if (!mergedSystems.isEmpty()) {
            target.setSystems(new ArrayList<>(mergedSystems.values()));
        }
    }

    private void mergeRuntime(OpenAdtConfig target, OpenAdtConfig.RuntimeConfig source) {
        if (source == null) {
            return;
        }
        OpenAdtConfig.RuntimeConfig runtime = target.getRuntime();
        if (runtime == null) {
            runtime = new OpenAdtConfig.RuntimeConfig();
            target.setRuntime(runtime);
        }
        if (source.getJcoJar() != null) {
            runtime.setJcoJar(source.getJcoJar());
        }
        if (source.getJcoNativeDir() != null) {
            runtime.setJcoNativeDir(source.getJcoNativeDir());
        }
        if (source.getSapcrypto() != null) {
            runtime.setSapcrypto(source.getSapcrypto());
        }
        if (source.getAdtPluginsDir() != null) {
            runtime.setAdtPluginsDir(source.getAdtPluginsDir());
        }
    }

    private void mergeRuntimeLastWins(OpenAdtConfig target, OpenAdtConfig.RuntimeConfig source) {
        if (source == null) {
            return;
        }
        OpenAdtConfig.RuntimeConfig runtime = target.getRuntime();
        if (runtime == null) {
            runtime = new OpenAdtConfig.RuntimeConfig();
            target.setRuntime(runtime);
        }
        mergeRuntime(target, source);
    }

    private void mergeSecureLoginLastWins(OpenAdtConfig target, OpenAdtConfig.SecureLoginConfig source) {
        mergeSecureLogin(target, source);
    }

    private void mergeSecureLogin(OpenAdtConfig target, OpenAdtConfig.SecureLoginConfig source) {
        if (source == null) {
            return;
        }
        OpenAdtConfig.SecureLoginConfig secureLogin = target.getSecureLogin();
        if (secureLogin == null) {
            secureLogin = new OpenAdtConfig.SecureLoginConfig();
            target.setSecureLogin(secureLogin);
        }
        if (source.getLocalSecurityHub() != null) {
            secureLogin.setLocalSecurityHub(source.getLocalSecurityHub());
        }
        if (source.getOrigin() != null) {
            secureLogin.setOrigin(source.getOrigin());
        }
        if (source.getReferer() != null) {
            secureLogin.setReferer(source.getReferer());
        }
        if (source.getWebAdapterProfileId() != null) {
            secureLogin.setWebAdapterProfileId(source.getWebAdapterProfileId());
        }
        if (source.getMysapsso2() != null) {
            secureLogin.setMysapsso2(source.getMysapsso2());
        }
    }

    private void mergeProxyLastWins(OpenAdtConfig target, OpenAdtConfig.ProxyConfig source) {
        mergeProxy(target, source);
    }

    private void mergeProxy(OpenAdtConfig target, OpenAdtConfig.ProxyConfig source) {
        if (source == null) {
            return;
        }
        OpenAdtConfig.ProxyConfig proxy = target.getProxy();
        if (proxy == null) {
            proxy = new OpenAdtConfig.ProxyConfig();
            target.setProxy(proxy);
        }
        if (source.getListen() != null) {
            proxy.setListen(source.getListen());
        }
        if (source.getAuth() != null) {
            proxy.setAuth(source.getAuth());
        }
        if (source.getUsername() != null) {
            proxy.setUsername(source.getUsername());
        }
    }

    private void mergeSystemLastWins(Map<String, SystemProfile> systems, SystemProfile source) {
        if (source == null) {
            return;
        }
        String key = source.getAlias() != null ? source.getAlias() : source.getSystemId();
        if (key == null) {
            return;
        }
        SystemProfile target = systems.get(key);
        if (target == null) {
            systems.put(key, source);
            return;
        }
        mergeSystem(systems, source);
        mergeAdtLastWins(target, source.getAdt());
    }

    private void mergeSystem(Map<String, SystemProfile> systems, SystemProfile source) {
        if (source == null) {
            return;
        }
        String key = source.getAlias() != null ? source.getAlias() : source.getSystemId();
        if (key == null) {
            return;
        }
        SystemProfile target = systems.get(key);
        if (target == null) {
            target = new SystemProfile();
            systems.put(key, target);
        }
        if (source.getAlias() != null) {
            target.setAlias(source.getAlias());
        }
        if (source.getSource() != null) {
            target.setSource(source.getSource());
        }
        if (source.getDescription() != null) {
            target.setDescription(source.getDescription());
        }
        if (source.getSystemId() != null) {
            target.setSystemId(source.getSystemId());
        }
        if (source.getClient() != null) {
            target.setClient(source.getClient());
        }
        if (source.getLanguage() != null) {
            target.setLanguage(source.getLanguage());
        }
        if (source.getUser() != null) {
            target.setUser(source.getUser());
        }
        mergeJco(target, source.getJco());
        mergeAdt(target, source.getAdt());
    }

    private void mergeJco(SystemProfile target, SystemProfile.JcoConfig source) {
        if (source == null) {
            return;
        }
        SystemProfile.JcoConfig jco = target.getJco();
        if (jco == null) {
            jco = new SystemProfile.JcoConfig();
            target.setJco(jco);
        }
        if (source.getMshost() != null) {
            jco.setMshost(source.getMshost());
        }
        if (source.getMsserv() != null) {
            jco.setMsserv(source.getMsserv());
        }
        if (source.getR3name() != null) {
            jco.setR3name(source.getR3name());
        }
        if (source.getGroup() != null) {
            jco.setGroup(source.getGroup());
        }
        if (source.getAshost() != null) {
            jco.setAshost(source.getAshost());
        }
        if (source.getSysnr() != null) {
            jco.setSysnr(source.getSysnr());
        }
        if (source.getSncMode() != null) {
            jco.setSncMode(source.getSncMode());
        }
        if (source.getSncQop() != null) {
            jco.setSncQop(source.getSncQop());
        }
        if (source.getSncPartnername() != null) {
            jco.setSncPartnername(source.getSncPartnername());
        }
        if (source.getSncSso() != null) {
            jco.setSncSso(source.getSncSso());
        }
        if (source.getSticky() != null) {
            jco.setSticky(source.getSticky());
        }
        if (source.getDenyInitialPassword() != null) {
            jco.setDenyInitialPassword(source.getDenyInitialPassword());
        }
    }

    private void mergeAdtLastWins(SystemProfile target, SystemProfile.AdtConfig source) {
        if (source == null) {
            return;
        }
        SystemProfile.AdtConfig adt = target.getAdt();
        if (adt == null) {
            adt = new SystemProfile.AdtConfig();
            target.setAdt(adt);
        }
        mergeAdt(target, source);
    }

    private void mergeAdt(SystemProfile target, SystemProfile.AdtConfig source) {
        if (source == null) {
            return;
        }
        SystemProfile.AdtConfig adt = target.getAdt();
        if (adt == null) {
            adt = new SystemProfile.AdtConfig();
            target.setAdt(adt);
        }
        if (source.getTransport() != null) {
            adt.setTransport(source.getTransport());
        }
        if (source.getAshost() != null) {
            adt.setAshost(source.getAshost());
        }
        if (source.getDiscoveryUrl() != null) {
            adt.setDiscoveryUrl(source.getDiscoveryUrl());
        }
        if (source.getAuthenticationKind() != null) {
            adt.setAuthenticationKind(source.getAuthenticationKind());
        }
    }

    private void writeEntrypoint(Path path, List<String> includes) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("version = 1");
        lines.add("");
        lines.add("[merge]");
        lines.add("strategy = \"last-wins\"");
        lines.add("includes = [");
        for (int i = 0; i < includes.size(); i++) {
            String suffix = i + 1 < includes.size() ? "," : "";
            lines.add("  \"" + includes.get(i) + "\"" + suffix);
        }
        lines.add("]");
        Files.writeString(path, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }

    private void writeDestinationsFragment(Path path, List<SystemProfile> systems) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("version = 1");
        if (systems != null) {
            for (SystemProfile system : systems) {
                String alias = system.getAlias() != null ? system.getAlias() : system.getSystemId();
                if (alias == null) {
                    continue;
                }
                lines.add("");
                lines.add("[destinations." + quoteKey(alias) + "]");
                writeString(lines, "alias", system.getAlias());
                writeString(lines, "source", system.getSource());
                writeString(lines, "description", system.getDescription());
                writeString(lines, "system_id", system.getSystemId());
                writeString(lines, "client", system.getClient());
                writeString(lines, "language", system.getLanguage());
                writeString(lines, "user", system.getUser());

                if (system.getJco() != null) {
                    lines.add("");
                    lines.add("[destinations." + quoteKey(alias) + ".jco]");
                    writeString(lines, "mshost", system.getJco().getMshost());
                    writeString(lines, "msserv", system.getJco().getMsserv());
                    writeString(lines, "r3name", system.getJco().getR3name());
                    writeString(lines, "group", system.getJco().getGroup());
                    writeString(lines, "ashost", system.getJco().getAshost());
                    writeString(lines, "sysnr", system.getJco().getSysnr());
                    writeString(lines, "snc_mode", system.getJco().getSncMode());
                    writeString(lines, "snc_qop", system.getJco().getSncQop());
                    writeString(lines, "snc_partnername", system.getJco().getSncPartnername());
                    writeString(lines, "snc_sso", system.getJco().getSncSso());
                    writeString(lines, "sticky", system.getJco().getSticky());
                    writeString(lines, "deny_initial_password", system.getJco().getDenyInitialPassword());
                }

                if (system.getAdt() != null) {
                    lines.add("");
                    lines.add("[destinations." + quoteKey(alias) + ".adt]");
                    writeString(lines, "transport", system.getAdt().getTransport());
                    writeString(lines, "ashost", system.getAdt().getAshost());
                    writeString(lines, "discovery_url", system.getAdt().getDiscoveryUrl());
                    writeString(lines, "authentication_kind", system.getAdt().getAuthenticationKind());
                }
            }
        }
        Files.writeString(path, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }

    private void writeLocalFragment(Path path, OpenAdtConfig config) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("version = 1");
        if (config.getRuntime() != null) {
            lines.add("");
            lines.add("[runtime]");
            writeString(lines, "jco_jar", config.getRuntime().getJcoJar());
            writeString(lines, "jco_native_dir", config.getRuntime().getJcoNativeDir());
            writeString(lines, "sapcrypto", config.getRuntime().getSapcrypto());
            writeString(lines, "adt_plugins_dir", config.getRuntime().getAdtPluginsDir());
        }
        if (config.getSecureLogin() != null) {
            lines.add("");
            lines.add("[secure_login]");
            writeString(lines, "local_security_hub", config.getSecureLogin().getLocalSecurityHub());
            writeString(lines, "origin", config.getSecureLogin().getOrigin());
            writeString(lines, "referer", config.getSecureLogin().getReferer());
            writeString(lines, "web_adapter_profile_id", config.getSecureLogin().getWebAdapterProfileId());
            writeString(lines, "mysapsso2", config.getSecureLogin().getMysapsso2());
        }
        if (config.getProxy() != null) {
            lines.add("");
            lines.add("[proxy]");
            writeString(lines, "listen", config.getProxy().getListen());
            writeString(lines, "auth", config.getProxy().getAuth());
            writeString(lines, "username", config.getProxy().getUsername());
        }
        Files.writeString(path, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }

    private void writeString(List<String> lines, String key, String value) {
        if (value != null) {
            lines.add(key + " = " + quoteValue(value));
        }
    }

    private String quoteKey(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String quoteValue(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private List<Path> configSearchPaths() {
        return List.of(localConfigPath(), globalConfigPath());
    }

    private Path localConfigPath() {
        return workingDirectory.resolve(".openadt").resolve("config.toml");
    }

    private Path globalConfigPath() {
        return homeDirectory.resolve(".openadt").resolve("config.toml");
    }

    private static Path normalizeEnvPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value);
    }

    private static class ConfigFragment {
        @JsonProperty("version")
        public int version;
        @JsonProperty("merge")
        public MergeConfig merge;
        @JsonProperty("runtime")
        public OpenAdtConfig.RuntimeConfig runtime;
        @JsonProperty("secure_login")
        public OpenAdtConfig.SecureLoginConfig secureLogin;
        @JsonProperty("proxy")
        public OpenAdtConfig.ProxyConfig proxy;
        @JsonProperty("systems")
        public List<SystemProfile> systems;
        @JsonProperty("destinations")
        public Map<String, SystemProfile> destinations;
    }

    private static class MergeConfig {
        @JsonProperty("strategy")
        public String strategy;
        @JsonProperty("includes")
        public List<String> includes;
    }
}
