package com.smartiptv.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dependency-free executable regression suite; also runs as Gradle :core:selfTest. */
public final class CoreSelfTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        var parsed = PlaylistParser.parse(new StringReader("\uFEFF#EXTM3U x-tvg-url=\"guide.xml\"\n#EXTINF:-1 tvg-id=\"news.1\" tvg-logo=\"logo.png\" group-title=\"News, World\",World News\n#EXTVLCOPT:http-user-agent=Custom Player\n#EXTVLCOPT:http-referrer=https://example.com/\nstreams/news.m3u8\n#EXTINF:-1,Duplicate\nstreams/news.m3u8\n#EXTINF:-1 tvg-name='Music' group-title='Music',Music\nhttps://example.com/music.ts|User-Agent=My%20Agent&Referer=https%3A%2F%2Fexample.com%2F\njavascript:alert(1)\n"),"https://example.com/playlist/list.m3u");
        eq(parsed.channels.size(),2,"duplicate/invalid streams filtered");
        var c=parsed.channels.get(0);
        eq(c.name,"World News","title"); eq(c.group,"News, World","quoted comma"); eq(c.epgId,"news.1","EPG identity");
        eq(c.url,"https://example.com/playlist/streams/news.m3u8","relative URL");
        eq(c.logo,"https://example.com/playlist/logo.png","relative logo");
        eq(c.headers.get("User-Agent"),"Custom Player","VLC user agent");
        eq(c.headers.get("Referer"),"https://example.com/","VLC referer");
        eq(parsed.channels.get(1).headers.get("User-Agent"),"My Agent","encoded headers");
        eq(parsed.epgUrl,"https://example.com/playlist/guide.xml","relative EPG");
        var hls=PlaylistParser.parse(new StringReader("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment01.ts\n#EXT-X-ENDLIST\n"),"https://example.com/live/index.m3u8");
        eq(hls.singleHls,true,"HLS recognised"); eq(hls.channels.size(),1,"segments are not channels");eq(hls.channels.get(0).url,"https://example.com/live/index.m3u8","original manifest");
        var master=PlaylistParser.parse(new StringReader("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\n720/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2560000\n1080/index.m3u8\n"),"https://example.com/master.m3u8");
        eq(master.channels.size(),1,"HLS variants are not channels");
        throwsIllegal(()->PlaylistParser.parse(new StringReader("#EXTM3U\n#EXT-X-TARGETDURATION:5\nsegment.ts"),null),"local HLS rejected");
        var bare=PlaylistParser.parse(new StringReader("#EXTM3U\nhttps://example.com/a\n#EXTINF:-1,Test\n#EXTGRP:Sports\nhttps://example.com/b\n"),null);
        eq(bare.channels.get(0).name,"Stream 1","unnamed stream");eq(bare.channels.get(1).group,"Sports","EXTGRP");
        throwsIllegal(()->UrlTools.requireHttp("file:///etc/passwd"),"file URL rejected");
        throwsIllegal(()->UrlTools.requireHttp("https://user:pass@example.com/a"),"URL userinfo rejected");
        throwsIllegal(()->UrlTools.server("https://example.com/?password=secret"),"server query rejected");
        throwsIllegal(()->UrlTools.server("https://example.com/player_api.php"),"API endpoint rejected as server");
        eq(UrlTools.stream("https://example.com:8080/","a/b","p&?","live","42","m3u8"),"https://example.com:8080/live/a%2Fb/p%26%3F/42.m3u8","Xtream path encoding");
        eq(UrlTools.api("https://example.com","a b","p&x","get_live_streams"),"https://example.com/player_api.php?username=a%20b&password=p%26x&action=get_live_streams","API query encoding");
        long now=XmlTvParser.timestamp("20261007080000 +0300");
        eq(now,XmlTvParser.timestamp("20261007050000 +0000"),"XMLTV timezone");
        eq(XmlTvParser.timestamp("bad"),0L,"invalid date");
        String xml="<?xml version='1.0'?><!DOCTYPE tv SYSTEM 'http://127.0.0.1:1/private'><tv><programme channel='news.1' start='20261007080000 +0300' stop='20261007090000 +0300'><title>Morning &amp; News</title><desc>Today</desc></programme><programme channel='old' start='20200101000000 +0000' stop='20200101010000 +0000'><title>Old</title></programme></tv>";
        var guide=XmlTvParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),now);
        eq(guide.size(),1,"expired EPG filtered; DTD not fetched");eq(guide.get(0).title,"Morning & News","XML entities");eq(guide.get(0).description,"Today","description");
        String xxe="<!DOCTYPE tv [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><tv><programme channel='a' start='20261007080000 +0300' stop='20261007090000 +0300'><title>&secret;</title></programme></tv>";
        eq(XmlTvParser.parse(new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8)),now).size(),0,"external entities disabled");
        var large=new StringBuilder("#EXTM3U\n");for(int i=0;i<20_000;i++) large.append("#EXTINF:-1 group-title=\"Group ").append(i%10).append("\",Channel ").append(i).append("\nhttps://example.com/").append(i).append(".m3u8\n");
        eq(PlaylistParser.parse(new StringReader(large.toString()),null).channels.size(),20_000,"large provider catalogue");
        System.out.println("PASS: "+assertions+" parser, URL, EPG and 20,000-channel assertions");
    }
    private static void eq(Object actual,Object expected,String message) {
        assertions++; if(!Objects.equals(actual,expected)) throw new AssertionError(message+": expected "+expected+", got "+actual);
    }
    private interface Checked { void run() throws Exception; }
    private static void throwsIllegal(Checked checked,String message) throws Exception {
        assertions++; try { checked.run(); } catch(IllegalArgumentException expected) { return; } throw new AssertionError(message);
    }
}
