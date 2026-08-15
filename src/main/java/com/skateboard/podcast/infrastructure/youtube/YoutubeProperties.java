package com.skateboard.podcast.infrastructure.youtube;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube")
public class YoutubeProperties {

    private Api api = new Api();
    private String channelId;
    private Sync sync = new Sync();

    public Api getApi()               { return api; }
    public void setApi(Api api)       { this.api = api; }
    public String getChannelId()      { return channelId; }
    public void setChannelId(String v){ this.channelId = v; }
    public Sync getSync()             { return sync; }
    public void setSync(Sync sync)    { this.sync = sync; }

    public static class Api {
        private String baseUrl = "https://www.googleapis.com/youtube/v3";
        private String key;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;

        public String getBaseUrl()             { return baseUrl; }
        public void setBaseUrl(String v)       { this.baseUrl = v; }
        public String getKey()                 { return key; }
        public void setKey(String v)           { this.key = v; }
        public int getConnectTimeoutMs()       { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int v) { this.connectTimeoutMs = v; }
        public int getReadTimeoutMs()          { return readTimeoutMs; }
        public void setReadTimeoutMs(int v)    { this.readTimeoutMs = v; }
    }

    public static class Sync {
        private boolean enabled = false;
        private String cron = "0 */10 * * * *";
        private int initialImportLimit = 20;

        public boolean isEnabled()              { return enabled; }
        public void setEnabled(boolean v)       { this.enabled = v; }
        public String getCron()                 { return cron; }
        public void setCron(String v)           { this.cron = v; }
        public int getInitialImportLimit()      { return initialImportLimit; }
        public void setInitialImportLimit(int v){ this.initialImportLimit = v; }
    }
}
