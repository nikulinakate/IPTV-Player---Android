package com.smartiptv.core;

import java.util.Map;

public final class ChannelEntry {
    public final String name, url, group, logo, epgId;
    public final Map<String, String> headers;
    public ChannelEntry(String name, String url, String group, String logo, String epgId, Map<String, String> headers) {
        this.name = name; this.url = url; this.group = group;
        this.logo = logo; this.epgId = epgId; this.headers = Map.copyOf(headers);
    }
}
