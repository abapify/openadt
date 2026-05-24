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
    private static final String VERSION_LINE = "version = 1";
    private static final String DESTINATIONS_PREFIX = "[destinations.";
    private static final String DESTINATIONS_DIR = "destinations";
    private static final String LOCAL_FRAGMENT_FILE = "local.openadt.toml";
    private static final String MANUAL_FRAGMENT_FILE = "manual.openadt.toml";
    private static final String DETECTED_FRAGMENT_FILE = "detected.openadt.toml";
    private static final String DESTINATIONS_GLOB = "destinations/*.openadt.toml";
    private static final String PROFILES_SEGMENT = ".profiles.";
    private static final String KEY_SSO_LANDING_URL = "sso_landing_url";
    private static final String KEY_ASHOST = "ashost";
    private static final String KEY_TRANSPORT = "transport";
    private static final String KEY_DISCOVERY_URL = "discovery_url";
    private static final String KEY_AUTHENTICATION_KIND = "authentication_kind";

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
        Path destinationsDir = rootDir.resolve(DESTINATIONS_DIR);
        Path destinationsFile = destinationsDir.resolve(DETECTED_FRAGMENT_FILE);
        Path localFile = rootDir.resolve(LOCAL_FRAGMENT_FILE);

        Files.createDirectories(destinationsDir);
        writeEntrypoint(normalizedEntrypoint, List.of(DESTINATIONS_GLOB, LOCAL_FRAGMENT_FILE));
        writeDestinationsFragment(destinationsFile, config.getSystems());
        writeLocalFragment(localFile, config);
    }

    /**
     * Creates or updates a destination profile in manual config storage.
     * Fragment-based entrypoints write to {@code destinations/manual.openadt.toml};
     * flat configs are updated in place.
     */
    public Path saveManualDestinationProfile(
        Path configPath,
        SystemProfile destination,
        String profileName,
        SystemProfile.ProfileConfig profile,
        boolean setDefaultProfile
    ) throws IOException {
        Path normalizedPath = configPath.toAbsolutePath().normalize();
        Path writeTarget = resolveManualWriteTarget(normalizedPath);

        OpenAdtConfig existing = Files.exists(normalizedPath) ? load(normalizedPath) : new OpenAdtConfig();
        LinkedHashMap<String, SystemProfile> systems = loadExistingSystems(existing);
        prepareTargetSystem(systems, destination, profileName, profile, setDefaultProfile);

        if (writeTarget.equals(normalizedPath)) {
            existing.setSystems(new ArrayList<>(systems.values()));
            writeFlatConfig(writeTarget, existing);
        } else {
            writeDestinationsFragment(writeTarget, new ArrayList<>(systems.values()));
        }
        return writeTarget;
    }

    private Path resolveManualWriteTarget(Path normalizedPath) throws IOException {
        if (!Files.exists(normalizedPath)) {
            Path rootDir = normalizedPath.getParent();
            Files.createDirectories(rootDir.resolve(DESTINATIONS_DIR));
            writeEntrypoint(normalizedPath, List.of(DESTINATIONS_GLOB, LOCAL_FRAGMENT_FILE));
            return rootDir.resolve(DESTINATIONS_DIR).resolve(MANUAL_FRAGMENT_FILE);
        }
        if (hasMergeIncludes(normalizedPath)) {
            Path writeTarget = normalizedPath.getParent().resolve(DESTINATIONS_DIR).resolve(MANUAL_FRAGMENT_FILE);
            Files.createDirectories(writeTarget.getParent());
            return writeTarget;
        }
        return normalizedPath;
    }

    private LinkedHashMap<String, SystemProfile> loadExistingSystems(OpenAdtConfig existing) {
        LinkedHashMap<String, SystemProfile> systems = new LinkedHashMap<>();
        if (existing.getSystems() != null) {
            for (SystemProfile system : existing.getSystems()) {
                mergeSystem(systems, system);
            }
        }
        return systems;
    }

    private void prepareTargetSystem(
        LinkedHashMap<String, SystemProfile> systems,
        SystemProfile destination,
        String profileName,
        SystemProfile.ProfileConfig profile,
        boolean setDefaultProfile
    ) {
        SystemProfile target = systems.computeIfAbsent(destination.getAlias(), ignored -> new SystemProfile());
        applyDestinationIdentityFields(target, destination);
        if (setDefaultProfile) {
            target.setDefaultProfile(profileName);
        }
        Map<String, SystemProfile.ProfileConfig> profiles = ensureProfiles(target);
        mergeProfile(profiles, profileName, profile);
    }

    private void applyDestinationIdentityFields(SystemProfile target, SystemProfile destination) {
        if (target.getAlias() == null) {
            target.setAlias(destination.getAlias());
        }
        if (destination.getDescription() != null) {
            target.setDescription(destination.getDescription());
        }
        if (destination.getSystemId() != null) {
            target.setSystemId(destination.getSystemId());
        }
        if (destination.getClient() != null) {
            target.setClient(destination.getClient());
        }
        if (destination.getLanguage() != null) {
            target.setLanguage(destination.getLanguage());
        }
        if (destination.getUser() != null) {
            target.setUser(destination.getUser());
        }
        if (destination.getSource() != null) {
            target.setSource(destination.getSource());
        }
        mergeJco(target, destination.getJco());
        mergeAdt(target, destination.getAdt());
    }

    private Map<String, SystemProfile.ProfileConfig> ensureProfiles(SystemProfile target) {
        Map<String, SystemProfile.ProfileConfig> profiles = target.getProfiles();
        if (profiles == null) {
            profiles = new LinkedHashMap<>();
            target.setProfiles(profiles);
        }
        return profiles;
    }

    boolean hasMergeIncludes(Path path) throws IOException {
        if (!Files.exists(path)) {
            return false;
        }
        ConfigFragment fragment = mapper.readValue(path.toFile(), ConfigFragment.class);
        return fragment.merge != null
            && fragment.merge.includes != null
            && !fragment.merge.includes.isEmpty();
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
        if (source.getHttpCaCert() != null) {
            runtime.setHttpCaCert(source.getHttpCaCert());
        }
        if (source.getHttpTruststore() != null) {
            runtime.setHttpTruststore(source.getHttpTruststore());
        }
        if (source.getHttpTruststorePassword() != null) {
            runtime.setHttpTruststorePassword(source.getHttpTruststorePassword());
        }
        if (source.getHttpCallbackPort() != null) {
            runtime.setHttpCallbackPort(source.getHttpCallbackPort());
        }
        if (source.getHttpCallbackHost() != null) {
            runtime.setHttpCallbackHost(source.getHttpCallbackHost());
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
        if (source.getDefaultProfile() != null) {
            target.setDefaultProfile(source.getDefaultProfile());
        }
        mergeProfiles(target, source.getProfiles());
        mergeJco(target, source.getJco());
        mergeAdt(target, source.getAdt());
    }

    private void mergeProfiles(SystemProfile target, Map<String, SystemProfile.ProfileConfig> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        Map<String, SystemProfile.ProfileConfig> profiles = target.getProfiles();
        if (profiles == null) {
            profiles = new LinkedHashMap<>();
            target.setProfiles(profiles);
        }
        for (Map.Entry<String, SystemProfile.ProfileConfig> entry : source.entrySet()) {
            mergeProfile(profiles, entry.getKey(), entry.getValue());
        }
    }

    private void mergeProfile(
        Map<String, SystemProfile.ProfileConfig> profiles,
        String name,
        SystemProfile.ProfileConfig source
    ) {
        if (source == null) {
            return;
        }
        SystemProfile.ProfileConfig target = profiles.computeIfAbsent(name, ignored -> new SystemProfile.ProfileConfig());
        if (source.getTransport() != null) {
            target.setTransport(source.getTransport());
        }
        if (source.getAuthenticationKind() != null) {
            target.setAuthenticationKind(source.getAuthenticationKind());
        }
        if (source.getDiscoveryUrl() != null) {
            target.setDiscoveryUrl(source.getDiscoveryUrl());
        }
        if (source.getCallbackPort() != null) {
            target.setCallbackPort(source.getCallbackPort());
        }
        if (source.getSsoLandingUrl() != null) {
            target.setSsoLandingUrl(source.getSsoLandingUrl());
        }
        if (source.getJco() != null) {
            SystemProfile.JcoConfig jco = target.getJco();
            if (jco == null) {
                jco = new SystemProfile.JcoConfig();
                target.setJco(jco);
            }
            mergeJcoInto(jco, source.getJco());
        }
        if (source.getAdt() != null) {
            SystemProfile.AdtConfig adt = target.getAdt();
            if (adt == null) {
                adt = new SystemProfile.AdtConfig();
                target.setAdt(adt);
            }
            mergeAdtInto(adt, source.getAdt());
        }
    }

    private void mergeJcoInto(SystemProfile.JcoConfig target, SystemProfile.JcoConfig source) {
        if (source.getMshost() != null) {
            target.setMshost(source.getMshost());
        }
        if (source.getMsserv() != null) {
            target.setMsserv(source.getMsserv());
        }
        if (source.getR3name() != null) {
            target.setR3name(source.getR3name());
        }
        if (source.getGroup() != null) {
            target.setGroup(source.getGroup());
        }
        if (source.getAshost() != null) {
            target.setAshost(source.getAshost());
        }
        if (source.getSysnr() != null) {
            target.setSysnr(source.getSysnr());
        }
        if (source.getSncMode() != null) {
            target.setSncMode(source.getSncMode());
        }
        if (source.getSncQop() != null) {
            target.setSncQop(source.getSncQop());
        }
        if (source.getSncPartnername() != null) {
            target.setSncPartnername(source.getSncPartnername());
        }
        if (source.getSncSso() != null) {
            target.setSncSso(source.getSncSso());
        }
        if (source.getSticky() != null) {
            target.setSticky(source.getSticky());
        }
        if (source.getDenyInitialPassword() != null) {
            target.setDenyInitialPassword(source.getDenyInitialPassword());
        }
    }

    private void mergeAdtInto(SystemProfile.AdtConfig target, SystemProfile.AdtConfig source) {
        if (source.getTransport() != null) {
            target.setTransport(source.getTransport());
        }
        if (source.getAshost() != null) {
            target.setAshost(source.getAshost());
        }
        if (source.getDiscoveryUrl() != null) {
            target.setDiscoveryUrl(source.getDiscoveryUrl());
        }
        if (source.getAuthenticationKind() != null) {
            target.setAuthenticationKind(source.getAuthenticationKind());
        }
        if (source.getSsoLandingUrl() != null) {
            target.setSsoLandingUrl(source.getSsoLandingUrl());
        }
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
        mergeJcoInto(jco, source);
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
        mergeAdtInto(adt, source);
    }

    private void writeEntrypoint(Path path, List<String> includes) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(VERSION_LINE);
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

    private void writeFlatConfig(Path path, OpenAdtConfig config) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(VERSION_LINE);
        if (config.getRuntime() != null) {
            lines.add("");
            lines.add("[runtime]");
            writeString(lines, "jco_jar", config.getRuntime().getJcoJar());
            writeString(lines, "jco_native_dir", config.getRuntime().getJcoNativeDir());
            writeString(lines, "sapcrypto", config.getRuntime().getSapcrypto());
            writeString(lines, "adt_plugins_dir", config.getRuntime().getAdtPluginsDir());
            writeString(lines, "http_ca_cert", config.getRuntime().getHttpCaCert());
            writeString(lines, "http_truststore", config.getRuntime().getHttpTruststore());
            writeString(lines, "http_truststore_password", config.getRuntime().getHttpTruststorePassword());
            writeString(lines, "http_callback_port", config.getRuntime().getHttpCallbackPort());
            writeString(lines, "http_callback_host", config.getRuntime().getHttpCallbackHost());
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
        if (config.getSystems() != null) {
            for (SystemProfile system : config.getSystems()) {
                writeSystemDestination(lines, system);
            }
        }
        Files.writeString(path, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }

    private void writeDestinationsFragment(Path path, List<SystemProfile> systems) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(VERSION_LINE);
        if (systems != null) {
            for (SystemProfile system : systems) {
                writeSystemDestination(lines, system);
            }
        }
        Files.writeString(path, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }

    private void writeSystemDestination(List<String> lines, SystemProfile system) {
        String alias = system.getAlias() != null ? system.getAlias() : system.getSystemId();
        if (alias == null) {
            return;
        }
        lines.add("");
        lines.add(DESTINATIONS_PREFIX + quoteKey(alias) + "]");
        writeString(lines, "alias", system.getAlias());
        writeString(lines, "source", system.getSource());
        writeString(lines, "description", system.getDescription());
        writeString(lines, "system_id", system.getSystemId());
        writeString(lines, "client", system.getClient());
        writeString(lines, "language", system.getLanguage());
        writeString(lines, "user", system.getUser());
        writeString(lines, "default_profile", system.getDefaultProfile());

        if (system.getJco() != null) {
            writeJcoSection(lines, alias, system.getJco());
        }
        if (system.getAdt() != null) {
            writeAdtSection(lines, alias, system.getAdt());
        }
        if (system.getProfiles() != null) {
            for (Map.Entry<String, SystemProfile.ProfileConfig> profileEntry : system.getProfiles().entrySet()) {
                writeProfileSection(lines, alias, profileEntry.getKey(), profileEntry.getValue());
            }
        }
    }

    private void writeJcoSection(List<String> lines, String alias, SystemProfile.JcoConfig jco) {
        lines.add("");
        lines.add(DESTINATIONS_PREFIX + quoteKey(alias) + ".jco]");
        writeJcoSectionFields(lines, jco);
    }

    private void writeAdtSection(List<String> lines, String alias, SystemProfile.AdtConfig adt) {
        lines.add("");
        lines.add(DESTINATIONS_PREFIX + quoteKey(alias) + ".adt]");
        writeAdtSectionFields(lines, adt);
    }

    private void writeProfileSection(
        List<String> lines,
        String alias,
        String profileName,
        SystemProfile.ProfileConfig profile
    ) {
        if (profile == null) {
            return;
        }
        String profilePrefix = DESTINATIONS_PREFIX + quoteKey(alias) + PROFILES_SEGMENT + quoteKey(profileName) + "]";
        lines.add("");
        lines.add(profilePrefix);
        writeString(lines, KEY_TRANSPORT, profile.getTransport());
        writeString(lines, KEY_AUTHENTICATION_KIND, profile.getAuthenticationKind());
        writeString(lines, KEY_DISCOVERY_URL, profile.getDiscoveryUrl());
        writeString(lines, "callback_port", profile.getCallbackPort());
        writeString(lines, KEY_SSO_LANDING_URL, profile.getSsoLandingUrl());

        if (profile.getJco() != null) {
            lines.add("");
            lines.add(DESTINATIONS_PREFIX + quoteKey(alias) + PROFILES_SEGMENT + quoteKey(profileName) + ".jco]");
            writeJcoSectionFields(lines, profile.getJco());
        }
        if (profile.getAdt() != null) {
            lines.add("");
            lines.add(DESTINATIONS_PREFIX + quoteKey(alias) + PROFILES_SEGMENT + quoteKey(profileName) + ".adt]");
            writeAdtSectionFields(lines, profile.getAdt());
        }
    }

    private void writeJcoSectionFields(List<String> lines, SystemProfile.JcoConfig jco) {
        writeString(lines, "mshost", jco.getMshost());
        writeString(lines, "msserv", jco.getMsserv());
        writeString(lines, "r3name", jco.getR3name());
        writeString(lines, "group", jco.getGroup());
        writeString(lines, KEY_ASHOST, jco.getAshost());
        writeString(lines, "sysnr", jco.getSysnr());
        writeString(lines, "snc_mode", jco.getSncMode());
        writeString(lines, "snc_qop", jco.getSncQop());
        writeString(lines, "snc_partnername", jco.getSncPartnername());
        writeString(lines, "snc_sso", jco.getSncSso());
        writeString(lines, "sticky", jco.getSticky());
        writeString(lines, "deny_initial_password", jco.getDenyInitialPassword());
    }

    private void writeAdtSectionFields(List<String> lines, SystemProfile.AdtConfig adt) {
        writeString(lines, KEY_TRANSPORT, adt.getTransport());
        writeString(lines, KEY_ASHOST, adt.getAshost());
        writeString(lines, KEY_DISCOVERY_URL, adt.getDiscoveryUrl());
        writeString(lines, KEY_AUTHENTICATION_KIND, adt.getAuthenticationKind());
        writeString(lines, KEY_SSO_LANDING_URL, adt.getSsoLandingUrl());
    }

    private void writeLocalFragment(Path path, OpenAdtConfig config) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(VERSION_LINE);
        if (config.getRuntime() != null) {
            lines.add("");
            lines.add("[runtime]");
            writeString(lines, "jco_jar", config.getRuntime().getJcoJar());
            writeString(lines, "jco_native_dir", config.getRuntime().getJcoNativeDir());
            writeString(lines, "sapcrypto", config.getRuntime().getSapcrypto());
            writeString(lines, "adt_plugins_dir", config.getRuntime().getAdtPluginsDir());
            writeString(lines, "http_ca_cert", config.getRuntime().getHttpCaCert());
            writeString(lines, "http_truststore", config.getRuntime().getHttpTruststore());
            writeString(lines, "http_truststore_password", config.getRuntime().getHttpTruststorePassword());
            writeString(lines, "http_callback_port", config.getRuntime().getHttpCallbackPort());
            writeString(lines, "http_callback_host", config.getRuntime().getHttpCallbackHost());
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
        return "\""
            + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
            + "\"";
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
