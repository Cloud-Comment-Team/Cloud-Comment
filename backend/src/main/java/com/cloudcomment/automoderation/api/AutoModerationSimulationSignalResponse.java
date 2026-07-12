package com.cloudcomment.automoderation.api;

import com.cloudcomment.comment.application.AutoModerationSignal;

record AutoModerationSimulationSignalResponse(
    String code,
    int score,
    String message
) {

    static AutoModerationSimulationSignalResponse from(AutoModerationSignal signal) {
        return new AutoModerationSimulationSignalResponse(signal.category(), signal.score(), signal.reason());
    }
}
