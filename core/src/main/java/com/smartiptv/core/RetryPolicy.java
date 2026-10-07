package com.smartiptv.core;

/** Bounded recovery: reset only after stable playback or an explicit user retry. */
public final class RetryPolicy {
    private int attempts;
    public long nextDelayMs() {
        if (attempts >= 5) return -1;
        return 1000L << attempts++;
    }
    public int attempts() { return attempts; }
    public void reset() { attempts = 0; }
    public static boolean retryHttp(int status) {
        return status == 408 || status == 429 || (status >= 500 && status <= 599);
    }
}
