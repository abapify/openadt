package org.openadt.core;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captures the self-signed certificate presented by a loopback TLS endpoint.
 * Probe-only trust-all logic is isolated here; callers pin the captured cert in a KeyStore.
 */
final class LoopbackHubTlsProbe {
    private LoopbackHubTlsProbe() {
    }

    static X509Certificate probeCertificate(int port) throws IOException {
        if (port < 1 || port > 65535) {
            throw new IOException("Refusing TLS probe for invalid loopback port: " + port);
        }
        InetAddress loopback = InetAddress.getLoopbackAddress();
        AtomicReference<X509Certificate> captured = new AtomicReference<>();
        try {
            SSLContext probeContext = SSLContext.getInstance("TLSv1.2");
            probeContext.init(null, new TrustManager[]{captureTrustManager(captured)}, new SecureRandom());
            try (SSLSocket socket = (SSLSocket) probeContext.getSocketFactory().createSocket()) {
                socket.connect(new InetSocketAddress(loopback, port), 5_000);
                socket.startHandshake();
            } catch (IOException handshakeFailure) {
                X509Certificate certificate = captured.get();
                if (certificate != null) {
                    return certificate;
                }
                throw handshakeFailure;
            }
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Failed TLS probe for loopback hub on port " + port, error);
        }
        X509Certificate certificate = captured.get();
        if (certificate == null) {
            throw new IOException("No certificate captured from loopback hub on port " + port);
        }
        return certificate;
    }

    @SuppressWarnings("java:S4830")
    private static X509TrustManager captureTrustManager(AtomicReference<X509Certificate> captured) {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
                throw new java.security.cert.CertificateException("Client certificates are not used for the local hub probe");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                if (chain != null && chain.length > 0 && chain[0] != null) {
                    captured.compareAndSet(null, chain[0]);
                }
                throw new java.security.cert.CertificateException("Loopback TLS probe only");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
