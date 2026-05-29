package org.openadt.sap.adt.fallback.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Locale;
import java.util.function.UnaryOperator;

import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
final class HttpTlsConfigurer {
    private final UnaryOperator<String> envProvider;

    HttpTlsConfigurer() {
        this(System::getenv);
    }

    HttpTlsConfigurer(UnaryOperator<String> envProvider) {
        this.envProvider = envProvider;
    }

    SSLContext buildSslContext(OpenAdtConfig config) {
        return buildSslContext(config, null);
    }

    SSLContext buildSslContext(OpenAdtConfig config, SystemProfile system) {
        String truststorePath = HttpTlsTrustResolver.resolveTruststore(config, system, envProvider);
        String truststorePassword = HttpTlsTrustResolver.resolveTruststorePassword(config, system, envProvider);
        String caCertPath = HttpTlsTrustResolver.resolveCaCert(config, system, envProvider);

        if (truststorePath == null && caCertPath == null) {
            return null;
        }

        try {
            KeyStore store = loadDefaultTrustStore();

            if (truststorePath != null) {
                importTruststoreEntries(store, Path.of(truststorePath), truststorePassword);
            }
            if (caCertPath != null) {
                importCertificates(store, Path.of(caCertPath));
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(store);
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, trustManagerFactory.getTrustManagers(), null);
            return context;
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException
            | java.security.KeyManagementException error) {
            throw new IllegalStateException("Failed to initialize HTTP TLS trust configuration: " + error.getMessage(), error);
        }
    }

    private KeyStore loadDefaultTrustStore()
        throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        int index = 0;
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509TrustManager) {
                for (X509Certificate certificate : x509TrustManager.getAcceptedIssuers()) {
                    store.setCertificateEntry("default-" + index++, certificate);
                }
            }
        }
        return store;
    }

    private void importTruststoreEntries(KeyStore target, Path truststorePath, String password)
        throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
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

    private void importCertificates(KeyStore target, Path certificatePath)
        throws IOException, CertificateException, KeyStoreException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream stream = Files.newInputStream(certificatePath)) {
            int index = 0;
            for (Certificate certificate : factory.generateCertificates(stream)) {
                target.setCertificateEntry("ca-cert-" + index++, certificate);
            }
        }
    }

    private String resolveStoreType(Path truststorePath) {
        String fileName = truststorePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".p12") || fileName.endsWith(".pfx")) {
            return "PKCS12";
        }
        return KeyStore.getDefaultType();
    }
}
