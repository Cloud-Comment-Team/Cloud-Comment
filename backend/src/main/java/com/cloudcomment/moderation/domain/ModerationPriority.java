package com.cloudcomment.moderation.domain;

public enum ModerationPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    public static ModerationPriority fromScore(int score) {
        if (score >= 760) {
            return URGENT;
        }
        if (score >= 600) {
            return HIGH;
        }
        if (score >= 300) {
            return MEDIUM;
        }
        return LOW;
    }
}
