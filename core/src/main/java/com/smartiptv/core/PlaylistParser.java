package com.smartiptv.core;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class PlaylistParser {
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))");
    public static final class Result {
        public final List<ChannelEntry> channels;
        public final String epgUrl;
        public final boolean singleHls;
        Result(List<ChannelEntry> channels, String epgUrl, boolean singleHls) {
            this.channels = List.copyOf(channels); this.epgUrl = epgUrl; this.singleHls = singleHls;
        }
    }
    public static Result parse(Reader reader, String baseUrl) throws IOException {
        BufferedReader lines = new BufferedReader(reader);
        List<ChannelEntry> channels = new ArrayList<>();
        Map<String, String> attributes = new HashMap<>(), headers = new HashMap<>();
        Set<String> seen = new HashSet<>();
        String line, name = "", group = "", epg = "";
        boolean hls = false;
        while ((line = lines.readLine()) != null) {
            line = line.replace("\uFEFF", "").trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTM3U")) {
                Map<String, String> a = attributes(line);
                String value = a.getOrDefault("url-tvg", a.getOrDefault("x-tvg-url", ""));
                if (!value.isEmpty()) { try { epg = UrlTools.resolve(baseUrl, value.split(",")[0]); } catch (IllegalArgumentException ignored) {} }
            } else if (line.startsWith("#EXT-X-")) { hls = true; }
            else if (line.startsWith("#EXTINF:")) {
                attributes = attributes(line); headers = new HashMap<>();
                int comma = separator(line);
                name = comma >= 0 ? line.substring(comma + 1).trim() : attributes.getOrDefault("tvg-name", "");
                group = attributes.getOrDefault("group-title", "");
            } else if (line.startsWith("#EXTGRP:")) { group = line.substring(8).trim(); }
            else if (line.startsWith("#EXTVLCOPT:")) {
                String option = line.substring(11); int equal = option.indexOf('=');
                if (equal > 0) {
                    String key = option.substring(0, equal).toLowerCase(Locale.ROOT);
                    if (key.equals("http-user-agent")) headers.put("User-Agent", option.substring(equal + 1));
                    if (key.equals("http-referrer") || key.equals("http-referer")) headers.put("Referer", option.substring(equal + 1));
                }
            } else if (!line.startsWith("#")) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) for (String pair : parts[1].split("&")) {
                    String[] p = pair.split("=", 2);
                    if (p.length == 2) {
                        if (p[0].equalsIgnoreCase("user-agent")) headers.put("User-Agent", decode(p[1]));
                        if (p[0].equalsIgnoreCase("referer")) headers.put("Referer", decode(p[1]));
                    }
                }
                try {
                    String url = UrlTools.resolve(baseUrl, parts[0].trim());
                    if (seen.add(url) && channels.size() < 100_000) {
                        String logo = attributes.getOrDefault("tvg-logo", "");
                        if (!logo.isEmpty()) { try { logo = UrlTools.resolve(baseUrl, logo); } catch (IllegalArgumentException ignored) { logo = ""; } }
                        channels.add(new ChannelEntry(name.isEmpty() ? "Stream " + (channels.size()+1) : name, url, group, logo,
                            attributes.getOrDefault("tvg-id", ""), headers));
                    }
                } catch (IllegalArgumentException ignored) {}
                attributes = new HashMap<>(); headers = new HashMap<>(); name = ""; group = "";
            }
        }
        // HLS segments/variants belong to a single stream, never to the channel catalogue.
        if (hls) {
            if (baseUrl == null) throw new IllegalArgumentException("A local HLS manifest needs its original URL");
            return new Result(List.of(new ChannelEntry("Live stream", UrlTools.requireHttp(baseUrl), "", "", "", Map.of())), "", true);
        }
        return new Result(channels, epg, false);
    }
    private static String decode(String value) { try { return URLDecoder.decode(value, StandardCharsets.UTF_8); } catch (IllegalArgumentException e) { return value; } }
    private static Map<String, String> attributes(String line) {
        Map<String, String> map = new HashMap<>(); Matcher matcher = ATTRIBUTE.matcher(line);
        while (matcher.find()) map.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2) != null ? matcher.group(2) : matcher.group(3) != null ? matcher.group(3) : matcher.group(4));
        return map;
    }
    private static int separator(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' || c == '"') { if (quote == c) quote = 0; else if (quote == 0) quote = c; }
            if (c == ',' && quote == 0) return i;
        }
        return -1;
    }
}
