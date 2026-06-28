package com.cloudcomment.moderation.api;

import java.util.UUID;

public record PerformerResponse(
    UUID id,
    String email
) {
}
