package org.openadt.core;

import java.io.File;
import java.nio.file.Path;

public final class JCoRuntimeBootstrap {
    private static final String JAVA_LIBRARY_PATH = "java.library.path";
    private static final String SNC_LIBRARY_PROPERTY = "jco.middleware.snc_lib";

    private JCoRuntimeBootstrap() {
    }

    public static void prepare(OpenAdtConfig.RuntimeConfig runtime) {
        if (runtime == null) {
            return;
        }
        if (runtime.getJcoNativeDir() != null && !runtime.getJcoNativeDir().isBlank()) {
            prependSystemProperty(JAVA_LIBRARY_PATH, runtime.getJcoNativeDir());
        }
        if (runtime.getSapcrypto() != null && !runtime.getSapcrypto().isBlank()) {
            System.setProperty(SNC_LIBRARY_PROPERTY, runtime.getSapcrypto());
            Path sapcrypto = Path.of(runtime.getSapcrypto());
            Path sapDir = sapcrypto.getParent();
            if (sapDir != null) {
                prependSystemProperty(JAVA_LIBRARY_PATH, sapDir.toString());
            }
        }
    }

    private static void prependSystemProperty(String key, String value) {
        String current = System.getProperty(key);
        if (current == null || current.isBlank()) {
            System.setProperty(key, value);
            return;
        }
        for (String entry : current.split(File.pathSeparator)) {
            if (value.equalsIgnoreCase(entry)) {
                return;
            }
        }
        System.setProperty(key, value + File.pathSeparator + current);
    }
}
