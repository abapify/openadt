package org.openadt.core;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.function.Function;

final class HttpTlsConfigurer {
    private final Function<String, String> envProvider;

    HttpTlsConfigurer() {
        this(System::getenv);
    }

    HttpTlsConfigurer(Function<String, String> envProvider) {
        this.envProvider = envProvider;
    }

    SSLContext buildSslContext(OpenAdtConfig config) {
        String truststorePath = runtime(config, OpenAdtConfig.RuntimeConfig::getHttpTruststore);
        if (truststorePath == null) {
            truststorePath = blankToNull(envProvider.apply("OPENADT_HTTP_TRUSTSTORE"));
        }

        String truststorePassword = runtime(config, OpenAdtConfig.RuntimeConfig::getHttpTruststorePassword);
        if (truststorePassword == null) {
            truststorePassword = blankToNull(envProvider.apply("OPENADT_HTTP_TRUSTSTORE_PASSWORD"));
        }

        String caCertPath = runtime(config, OpenAdtConfig.RuntimeConfig::getHttpCaCert);
        if (caCertPath == null) {
            caCertPath = blankToNull(envProvider.apply("OPENADT_HTTP_CA_CERT"));
        }

        if (truststorePath == null && caCertPath == null) {
            return null;
        }

        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);

            if (truststorePath != null) {
                importTruststoreEntries(store, Path.of(truststorePath), truststorePassword);
            }
            if (caCertPath != null) {
                importCertificate(store, Path.of(caCertPath));
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagerFactory.getTrustManagers(), null);
            return context;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to initialize HTTP TLS trust configuration: " + error.getMessage(), error);
        }
    }

    private void importTruststoreEntries(KeyStore target, Path truststorePath, String password) throws Exception {
        KeyStore loaded = KeyStore.getInstance(resolveStoreType(truststorePath));
        char[] passphrase = password != null ? password.toCharArray() : null;
        try (InputStream stream = Files.newInputStream(truststorePath)) {
            loaded.load(stream, passphrase);
        }
        Enumeration<String> aliases = loaded.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!loaded.isCertificateEntry(alias) && !loaded.isKeyEntry(alias)) {
                continue;
            }
            target.setCertificateEntry("truststore-" + alias, loaded.getCertificate(alias));
        }
    }

    private void importCertificate(KeyStore target, Path certificatePath) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream stream = Files.newInputStream(certificatePath)) {
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(stream);
            target.setCertificateEntry("ca-cert", certificate);
        }
    }

    private String resolveStoreType(Path truststorePath) {
        String fileName = truststorePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".p12") || fileName.endsWith(".pfx")) {
            return "PKCS12";
        }
        return KeyStore.getDefaultType();
    }

    private static String runtime(OpenAdtConfig config, Function<OpenAdtConfig.RuntimeConfig, String> getter) {
        if (config == null || config.getRuntime() == null) {
            return null;
        }
        return blankToNull(getter.apply(config.getRuntime()));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
