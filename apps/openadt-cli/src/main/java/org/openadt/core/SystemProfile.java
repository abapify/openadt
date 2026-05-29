package org.openadt.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class SystemProfile {
    private String alias;
    private String source;
    private String description;
    @JsonProperty("system_id")
    private String systemId;
    private String client;
    private String language;
    private String user;
    @JsonProperty("default_profile")
    private String defaultProfile;
    private Map<String, ProfileConfig> profiles;
    private JcoConfig jco;
    private AdtConfig adt;
    /** Set by {@link DestinationProfileResolver} when {@code --profile} or {@code default_profile} applies; not persisted in TOML. */
    @JsonIgnore
    private String activeProfile;

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSystemId() { return systemId; }
    public void setSystemId(String systemId) { this.systemId = systemId; }
    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getDefaultProfile() { return defaultProfile; }
    public void setDefaultProfile(String defaultProfile) { this.defaultProfile = defaultProfile; }
    public Map<String, ProfileConfig> getProfiles() { return profiles; }
    public void setProfiles(Map<String, ProfileConfig> profiles) { this.profiles = profiles; }
    public JcoConfig getJco() { return jco; }
    public void setJco(JcoConfig jco) { this.jco = jco; }
    public AdtConfig getAdt() { return adt; }
    public void setAdt(AdtConfig adt) { this.adt = adt; }
    public String getActiveProfile() { return activeProfile; }
    public void setActiveProfile(String activeProfile) { this.activeProfile = activeProfile; }

    public static class JcoConfig {
        private String mshost;
        private String msserv;
        private String r3name;
        private String group;
        private String ashost;
        private String sysnr;
        @JsonProperty("snc_mode")
        private String sncMode;
        @JsonProperty("snc_qop")
        private String sncQop;
        @JsonProperty("snc_partnername")
        private String sncPartnername;
        @JsonProperty("snc_sso")
        private String sncSso;
        private String sticky;
        @JsonProperty("deny_initial_password")
        private String denyInitialPassword;

        public String getMshost() { return mshost; }
        public void setMshost(String mshost) { this.mshost = mshost; }
        public String getMsserv() { return msserv; }
        public void setMsserv(String msserv) { this.msserv = msserv; }
        public String getR3name() { return r3name; }
        public void setR3name(String r3name) { this.r3name = r3name; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getAshost() { return ashost; }
        public void setAshost(String ashost) { this.ashost = ashost; }
        public String getSysnr() { return sysnr; }
        public void setSysnr(String sysnr) { this.sysnr = sysnr; }
        public String getSncMode() { return sncMode; }
        public void setSncMode(String sncMode) { this.sncMode = sncMode; }
        public String getSncQop() { return sncQop; }
        public void setSncQop(String sncQop) { this.sncQop = sncQop; }
        public String getSncPartnername() { return sncPartnername; }
        public void setSncPartnername(String sncPartnername) { this.sncPartnername = sncPartnername; }
        public String getSncSso() { return sncSso; }
        public void setSncSso(String sncSso) { this.sncSso = sncSso; }
        public String getSticky() { return sticky; }
        public void setSticky(String sticky) { this.sticky = sticky; }
        public String getDenyInitialPassword() { return denyInitialPassword; }
        public void setDenyInitialPassword(String denyInitialPassword) { this.denyInitialPassword = denyInitialPassword; }
    }

    public static class AdtConfig {
        private String transport;
        private String ashost;
        @JsonProperty("discovery_url")
        private String discoveryUrl;
        @JsonProperty("authentication_kind")
        private String authenticationKind;
        @JsonProperty("sso_landing_url")
        private String ssoLandingUrl;
        @JsonProperty("http_ca_cert")
        private String httpCaCert;
        @JsonProperty("http_truststore")
        private String httpTruststore;
        @JsonProperty("http_truststore_password")
        private String httpTruststorePassword;

        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }
        public String getAshost() { return ashost; }
        public void setAshost(String ashost) { this.ashost = ashost; }
        public String getDiscoveryUrl() { return discoveryUrl; }
        public void setDiscoveryUrl(String discoveryUrl) { this.discoveryUrl = discoveryUrl; }
        public String getAuthenticationKind() { return authenticationKind; }
        public void setAuthenticationKind(String authenticationKind) { this.authenticationKind = authenticationKind; }
        public String getSsoLandingUrl() { return ssoLandingUrl; }
        public void setSsoLandingUrl(String ssoLandingUrl) { this.ssoLandingUrl = ssoLandingUrl; }
        public String getHttpCaCert() { return httpCaCert; }
        public void setHttpCaCert(String httpCaCert) { this.httpCaCert = httpCaCert; }
        public String getHttpTruststore() { return httpTruststore; }
        public void setHttpTruststore(String httpTruststore) { this.httpTruststore = httpTruststore; }
        public String getHttpTruststorePassword() { return httpTruststorePassword; }
        public void setHttpTruststorePassword(String httpTruststorePassword) {
            this.httpTruststorePassword = httpTruststorePassword;
        }
    }

    public static class ProfileConfig {
        private String transport;
        @JsonProperty("authentication_kind")
        private String authenticationKind;
        @JsonProperty("discovery_url")
        private String discoveryUrl;
        @JsonProperty("callback_port")
        private String callbackPort;
        @JsonProperty("sso_landing_url")
        private String ssoLandingUrl;
        @JsonProperty("http_ca_cert")
        private String httpCaCert;
        @JsonProperty("http_truststore")
        private String httpTruststore;
        @JsonProperty("http_truststore_password")
        private String httpTruststorePassword;
        private JcoConfig jco;
        private AdtConfig adt;

        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }
        public String getAuthenticationKind() { return authenticationKind; }
        public void setAuthenticationKind(String authenticationKind) { this.authenticationKind = authenticationKind; }
        public String getDiscoveryUrl() { return discoveryUrl; }
        public void setDiscoveryUrl(String discoveryUrl) { this.discoveryUrl = discoveryUrl; }
        public String getCallbackPort() { return callbackPort; }
        public void setCallbackPort(String callbackPort) { this.callbackPort = callbackPort; }
        public String getSsoLandingUrl() { return ssoLandingUrl; }
        public void setSsoLandingUrl(String ssoLandingUrl) { this.ssoLandingUrl = ssoLandingUrl; }
        public String getHttpCaCert() { return httpCaCert; }
        public void setHttpCaCert(String httpCaCert) { this.httpCaCert = httpCaCert; }
        public String getHttpTruststore() { return httpTruststore; }
        public void setHttpTruststore(String httpTruststore) { this.httpTruststore = httpTruststore; }
        public String getHttpTruststorePassword() { return httpTruststorePassword; }
        public void setHttpTruststorePassword(String httpTruststorePassword) {
            this.httpTruststorePassword = httpTruststorePassword;
        }
        public JcoConfig getJco() { return jco; }
        public void setJco(JcoConfig jco) { this.jco = jco; }
        public AdtConfig getAdt() { return adt; }
        public void setAdt(AdtConfig adt) { this.adt = adt; }
    }
}
