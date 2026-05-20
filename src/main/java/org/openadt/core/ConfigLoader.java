package org.openadt.core;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
    private static final String WINDOWS_CONFIG = System.getenv("APPDATA") != null
        ? System.getenv("APPDATA") + "\\OpenADT\\config.toml"
        : null;
    private static final String UNIX_CONFIG = System.getProperty("user.home") + "/.config/openadt/config.toml";

    private final TomlMapper mapper;

    public ConfigLoader() {
        this.mapper = new TomlMapper();
    }

    public Path getDefaultConfigPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") && WINDOWS_CONFIG != null) {
            return Path.of(WINDOWS_CONFIG);
        }
        return Path.of(UNIX_CONFIG);
    }

    public OpenAdtConfig load() throws IOException {
        return load(getDefaultConfigPath());
    }

    public OpenAdtConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new OpenAdtConfig();
        }
        return mapper.readValue(path.toFile(), OpenAdtConfig.class);
    }

    public void save(OpenAdtConfig config, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        mapper.writeValue(path.toFile(), config);
    }
}
