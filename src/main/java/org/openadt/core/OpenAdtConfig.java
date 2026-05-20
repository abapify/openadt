package org.openadt.core;

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

        public String getJcoJar() { return jcoJar; }
        public void setJcoJar(String jcoJar) { this.jcoJar = jcoJar; }
        public String getJcoNativeDir() { return jcoNativeDir; }
        public void setJcoNativeDir(String jcoNativeDir) { this.jcoNativeDir = jcoNativeDir; }
        public String getSapcrypto() { return sapcrypto; }
        public void setSapcrypto(String sapcrypto) { this.sapcrypto = sapcrypto; }
    }

    public static class SecureLoginConfig {
        @JsonProperty("local_security_hub")
        private String localSecurityHub;
        private String origin;
        private String referer;

        public String getLocalSecurityHub() { return localSecurityHub; }
        public void setLocalSecurityHub(String localSecurityHub) { this.localSecurityHub = localSecurityHub; }
        public String getOrigin() { return origin; }
        public void setOrigin(String origin) { this.origin = origin; }
        public String getReferer() { return referer; }
        public void setReferer(String referer) { this.referer = referer; }
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
