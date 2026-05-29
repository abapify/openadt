package org.openadt.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OpenAdtConfig {
    private int version;
    private RuntimeConfig runtime;
    @JsonProperty("secure_login")
    private SecureLoginConfig secureLogin;
    private ProxyConfig proxy;
    private List<SystemProfile> systems;

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public RuntimeConfig getRuntime() { return runtime; }
    public void setRuntime(RuntimeConfig runtime) { this.runtime = runtime; }
    public SecureLoginConfig getSecureLogin() { return secureLogin; }
    public void setSecureLogin(SecureLoginConfig secureLogin) { this.secureLogin = secureLogin; }
    public ProxyConfig getProxy() { return proxy; }
    public void setProxy(ProxyConfig proxy) { this.proxy = proxy; }
    public List<SystemProfile> getSystems() { return systems; }
    public void setSystems(List<SystemProfile> systems) { this.systems = systems; }

    public static class RuntimeConfig {
        @JsonProperty("jco_jar")
        private String jcoJar;
        @JsonProperty("jco_native_dir")
        private String jcoNativeDir;
        private String sapcrypto;
        @JsonProperty("adt_plugins_dir")
        private String adtPluginsDir;
        @JsonProperty("http_ca_cert")
        private String httpCaCert;
        @JsonProperty("http_truststore")
        private String httpTruststore;
        @JsonProperty("http_truststore_password")
        private String httpTruststorePassword;
        @JsonProperty("http_callback_port")
        private String httpCallbackPort;
        @JsonProperty("http_callback_host")
        private String httpCallbackHost;

        public String getJcoJar() { return jcoJar; }
        public void setJcoJar(String jcoJar) { this.jcoJar = jcoJar; }
        public String getJcoNativeDir() { return jcoNativeDir; }
        public void setJcoNativeDir(String jcoNativeDir) { this.jcoNativeDir = jcoNativeDir; }
        public String getSapcrypto() { return sapcrypto; }
        public void setSapcrypto(String sapcrypto) { this.sapcrypto = sapcrypto; }
        public String getAdtPluginsDir() { return adtPluginsDir; }
        public void setAdtPluginsDir(String adtPluginsDir) { this.adtPluginsDir = adtPluginsDir; }
        public String getHttpCaCert() { return httpCaCert; }
        public void setHttpCaCert(String httpCaCert) { this.httpCaCert = httpCaCert; }
        public String getHttpTruststore() { return httpTruststore; }
        public void setHttpTruststore(String httpTruststore) { this.httpTruststore = httpTruststore; }
        public String getHttpTruststorePassword() { return httpTruststorePassword; }
        public void setHttpTruststorePassword(String httpTruststorePassword) { this.httpTruststorePassword = httpTruststorePassword; }
        public String getHttpCallbackPort() { return httpCallbackPort; }
        public void setHttpCallbackPort(String httpCallbackPort) { this.httpCallbackPort = httpCallbackPort; }
        public String getHttpCallbackHost() { return httpCallbackHost; }
        public void setHttpCallbackHost(String httpCallbackHost) { this.httpCallbackHost = httpCallbackHost; }
    }

    public static class SecureLoginConfig {
        @JsonProperty("local_security_hub")
        private String localSecurityHub;
        private String origin;
        private String referer;
        @JsonProperty("web_adapter_profile_id")
        private String webAdapterProfileId;
        @JsonProperty("enroll_url")
        private String enrollUrl;
        @JsonProperty("sso_url")
        private String ssoUrl;
        private String mysapsso2;

        public String getLocalSecurityHub() { return localSecurityHub; }
        public void setLocalSecurityHub(String localSecurityHub) { this.localSecurityHub = localSecurityHub; }
        public String getOrigin() { return origin; }
        public void setOrigin(String origin) { this.origin = origin; }
        public String getReferer() { return referer; }
        public void setReferer(String referer) { this.referer = referer; }
        public String getWebAdapterProfileId() { return webAdapterProfileId; }
        public void setWebAdapterProfileId(String webAdapterProfileId) { this.webAdapterProfileId = webAdapterProfileId; }
        public String getEnrollUrl() { return enrollUrl; }
        public void setEnrollUrl(String enrollUrl) { this.enrollUrl = enrollUrl; }
        public String getSsoUrl() { return ssoUrl; }
        public void setSsoUrl(String ssoUrl) { this.ssoUrl = ssoUrl; }
        public String getMysapsso2() { return mysapsso2; }
        public void setMysapsso2(String mysapsso2) { this.mysapsso2 = mysapsso2; }
    }

    public static class ProxyConfig {
        private String listen;
        private String auth;
        private String username;

        public String getListen() { return listen; }
        public void setListen(String listen) { this.listen = listen; }
        public String getAuth() { return auth; }
        public void setAuth(String auth) { this.auth = auth; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }
}
