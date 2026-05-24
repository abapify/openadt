package org.openadt.core;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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

    static final class UnavailableHubException extends IOException {
        UnavailableHubException(String message) {
            super(message);
        }

        UnavailableHubException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static boolean isUnavailableHubFailure(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof UnavailableHubException) {
                return true;
            }
            if (cause instanceof ConnectException || cause instanceof SocketTimeoutException) {
                return true;
            }
            if (cause instanceof SocketException && !(cause instanceof java.net.BindException)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    static X509Certificate probeCertificate(InetAddress loopback, int port) throws IOException {
        if (loopback == null || !loopback.isLoopbackAddress()) {
            throw new IOException("Refusing TLS probe for non-loopback address: " + loopback);
        }
        if (port < 1 || port > 65535) {
            throw new IOException("Refusing TLS probe for invalid loopback port: " + port);
        }
        AtomicReference<X509Certificate> captured = new AtomicReference<>();
        try {
            SSLContext probeContext = SSLContext.getInstance("TLSv1.2");
            probeContext.init(null, new TrustManager[]{captureTrustManager(captured)}, new SecureRandom());
            X509Certificate handshakeCertificate = captureViaHandshake(probeContext, loopback, port, captured);
            if (handshakeCertificate != null) {
                return handshakeCertificate;
            }
        } catch (UnavailableHubException error) {
            throw error;
        } catch (IOException error) {
            throw asUnavailableHub(error, port);
        } catch (Exception error) {
            throw asUnavailableHub(error, port);
        }
        X509Certificate certificate = captured.get();
        if (certificate == null) {
            throw new UnavailableHubException("No certificate captured from loopback hub on port " + port);
        }
        return certificate;
    }

    private static UnavailableHubException asUnavailableHub(Throwable error, int port) {
        if (isUnavailableHubFailure(error)) {
            return new UnavailableHubException(
                "Loopback Secure Login hub is unavailable on port " + port,
                error
            );
        }
        return new UnavailableHubException("Failed TLS probe for loopback hub on port " + port, error);
    }

    private static X509Certificate captureViaHandshake(
        SSLContext probeContext,
        InetAddress loopback,
        int port,
        AtomicReference<X509Certificate> captured
    ) throws IOException {
        try (SSLSocket socket = (SSLSocket) probeContext.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress(loopback, port), 5_000);
            socket.setSoTimeout(5_000);
            socket.startHandshake();
        } catch (IOException handshakeFailure) {
            X509Certificate certificate = captured.get();
            if (certificate != null) {
                return certificate;
            }
            if (isUnavailableHubFailure(handshakeFailure)) {
                throw new UnavailableHubException(
                    "Loopback Secure Login hub is unavailable on port " + port,
                    handshakeFailure
                );
            }
            throw handshakeFailure;
        }
        return null;
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
