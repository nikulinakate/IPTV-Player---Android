package com.smartiptv.core;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class UrlTools {
    private UrlTools() {}
    public static String requireHttp(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Invalid HTTP URL");
            return uri.toASCIIString();
        } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid HTTP URL"); }
    }
    public static String resolve(String base, String relative) {
        return requireHttp(base == null ? relative : URI.create(base).resolve(relative).toString());
    }
    public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    public static String server(String raw) {
        URI uri = URI.create(requireHttp(raw));
        if (uri.getRawQuery() != null) throw new IllegalArgumentException("Server URL must not contain a query");
        if (uri.getPath() != null && uri.getPath().matches("(?i).*/(?:player_api|get|xmltv)\\.php/?$"))
            throw new IllegalArgumentException("Use the provider server address");
        return uri.toString().replaceAll("/+$", "");
    }
    public static String api(String server, String username, String password, String action) {
        return server(server) + "/player_api.php?username=" + encode(username) + "&password=" + encode(password)
            + (action.isEmpty() ? "" : "&action=" + encode(action));
    }
    public static String stream(String server, String username, String password, String type, String id, String extension) {
        return server(server) + "/" + type + "/" + encode(username) + "/" + encode(password) + "/" + encode(id)
            + "." + encode(extension.isEmpty() ? "mp4" : extension);
    }
}
