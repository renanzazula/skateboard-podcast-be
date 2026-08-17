package com.skateboard.podcast.infrastructure.spotify;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotify")
public class SpotifyProperties {

    private Api api = new Api();
    private String showId;
    // Spotify's /shows/{id}/episodes endpoint filters by market; without a user
    // token (client-credentials flow has none) an explicit market avoids an
    // empty/unpredictable result set.
    private String market;
    private Sync sync = new Sync();

    public Api getApi()               { return api; }
    public void setApi(Api api)      { this.api = api; }
    public String getShowId()         { return showId; }
    public void setShowId(String v)   { this.showId = v; }
    public String getMarket()         { return market; }
    public void setMarket(String v)   { this.market = v; }
    public Sync getSync()             { return sync; }
    public void setSync(Sync sync)   { this.sync = sync; }

    public static class Api {
        private String baseUrl = "https://api.spotify.com";
        private String tokenUrl = "https://accounts.spotify.com/api/token";
        private String clientId;
        private String clientSecret;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;

        public String getBaseUrl()              { return baseUrl; }
        public void setBaseUrl(String v)        { this.baseUrl = v; }
        public String getTokenUrl()             { return tokenUrl; }
        public void setTokenUrl(String v)       { this.tokenUrl = v; }
        public String getClientId()             { return clientId; }
        public void setClientId(String v)       { this.clientId = v; }
        public String getClientSecret()         { return clientSecret; }
        public void setClientSecret(String v)   { this.clientSecret = v; }
        public int getConnectTimeoutMs()        { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int v)  { this.connectTimeoutMs = v; }
        public int getReadTimeoutMs()           { return readTimeoutMs; }
        public void setReadTimeoutMs(int v)     { this.readTimeoutMs = v; }
    }

    public static class Sync {
        private boolean enabled = false;

        public boolean isEnabled()        { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }
}
