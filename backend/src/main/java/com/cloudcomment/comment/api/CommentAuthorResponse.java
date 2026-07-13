package com.cloudcomment.comment.api;

import com.cloudcomment.comment.domain.CommentAuthor;

import java.util.UUID;

record CommentAuthorResponse(
    UUID id,
    String displayName
) {

    private static final String ANONYMOUS_DISPLAY_NAME = "Участник";

    static CommentAuthorResponse from(CommentAuthor author) {
        return new CommentAuthorResponse(author.id(), publicDisplayName(author));
    }

    static String publicDisplayName(CommentAuthor author) {
        String displayName = author.displayName();
        if (displayName == null) {
            return ANONYMOUS_DISPLAY_NAME;
        }

        String normalized = displayName.trim();
        if (normalized.isEmpty()
            || normalized.contains("@")
            || normalized.equalsIgnoreCase(author.email())) {
            return ANONYMOUS_DISPLAY_NAME;
        }

        return normalized;
    }
}
