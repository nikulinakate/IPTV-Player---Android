package com.smartiptv.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ChannelIdentity {
    private static final char[] HEX="0123456789abcdef".toCharArray();
    public static String id(String source,String key) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest((source+":"+key).getBytes(StandardCharsets.UTF_8));
            char[] result=new char[bytes.length*2];
            for(int i=0;i<bytes.length;i++) {
                int value=bytes[i]&255;result[i*2]=HEX[value>>>4];result[i*2+1]=HEX[value&15];
            }
            return new String(result);
        } catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
