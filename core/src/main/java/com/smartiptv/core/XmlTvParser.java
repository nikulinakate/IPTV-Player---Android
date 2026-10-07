package com.smartiptv.core;

import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

public final class XmlTvParser {
    public static final class Programme {
        public final String channelId, title, description;
        public final long start, stop;
        Programme(String id, String title, String description, long start, long stop) {
            this.channelId=id; this.title=title; this.description=description; this.start=start; this.stop=stop;
        }
    }
    public static List<Programme> parse(InputStream input, long now) throws Exception {
        List<Programme> result = new ArrayList<>();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        SAXParser parser = factory.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        // XMLTV commonly declares an external DTD. Never fetch it.
        reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        reader.setContentHandler(new DefaultHandler() {
            String id = "", title = "", description = "", tag = "";
            long start, stop; boolean programme; StringBuilder text = new StringBuilder();
            @Override public void startElement(String uri, String local, String name, Attributes a) {
                tag = name; text.setLength(0);
                if (name.equals("programme")) {
                    programme = true; id = a.getValue("channel"); title = ""; description = "";
                    start = timestamp(a.getValue("start")); stop = timestamp(a.getValue("stop"));
                }
            }
            @Override public void characters(char[] chars, int offset, int length) {
                if (programme && (tag.equals("title") || tag.equals("desc")) && text.length() < 16_384) text.append(chars, offset, Math.min(length, 16_384-text.length()));
            }
            @Override public void endElement(String uri, String local, String name) {
                if (name.equals("title") && title.isEmpty()) title = text.toString().trim();
                if (name.equals("desc") && description.isEmpty()) description = text.toString().trim();
                if (name.equals("programme")) {
                    if (id != null && !title.isEmpty() && start > 0 && stop > start && stop > now-3_600_000 && start < now+7*86_400_000L && result.size()<250_000)
                        result.add(new Programme(id, title, description, start, stop));
                    programme = false;
                }
                tag = ""; text.setLength(0);
            }
        });
        reader.parse(new InputSource(input));
        return result;
    }
    public static long timestamp(String value) {
        if (value == null) return 0;
        try {
            String s = value.trim();
            String date = s.substring(0, 14);
            ZoneOffset offset = s.length() > 14 ? ZoneOffset.of(s.substring(14).trim()) : ZoneOffset.UTC;
            return LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toInstant(offset).toEpochMilli();
        } catch (RuntimeException e) { return 0; }
    }
}
