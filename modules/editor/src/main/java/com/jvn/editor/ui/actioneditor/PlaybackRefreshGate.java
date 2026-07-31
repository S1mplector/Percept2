package com.jvn.editor.ui.actioneditor;

/**
 * Limits expensive editor chrome refreshes while frame-critical preview rendering stays pulse
 * driven.
 */
final class PlaybackRefreshGate {
    private final long intervalNanos;
    private long lastRefreshNanos = Long.MIN_VALUE;

    PlaybackRefreshGate(long intervalNanos) {
        if (intervalNanos <= 0) {
            throw new IllegalArgumentException("intervalNanos must be positive");
        }
        this.intervalNanos = intervalNanos;
    }

    boolean shouldRefresh(long nowNanos) {
        if (lastRefreshNanos == Long.MIN_VALUE
            || nowNanos < lastRefreshNanos
            || nowNanos - lastRefreshNanos >= intervalNanos) {
            lastRefreshNanos = nowNanos;
            return true;
        }
        return false;
    }

    void reset() {
        lastRefreshNanos = Long.MIN_VALUE;
    }
}
