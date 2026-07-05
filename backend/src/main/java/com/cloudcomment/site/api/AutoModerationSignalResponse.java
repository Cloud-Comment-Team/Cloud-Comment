package com.cloudcomment.site.api;

import com.cloudcomment.comment.application.AutoModerationSignal;

record AutoModerationSignalResponse(
    String category,
    int score,
    String reason
) {

    static AutoModerationSignalResponse from(AutoModerationSignal signal) {
        return new AutoModerationSignalResponse(
            signal.category(),
            signal.score(),
            signal.reason()
        );
    }
}
