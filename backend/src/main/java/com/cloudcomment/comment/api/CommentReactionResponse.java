package com.cloudcomment.comment.api;

import com.cloudcomment.comment.domain.CommentReactionSummary;
import com.cloudcomment.comment.domain.CommentReactionType;

record CommentReactionResponse(
    CommentReactionType type,
    String emoji,
    String label,
    long count,
    boolean reactedByCurrentUser
) {

    static CommentReactionResponse from(CommentReactionSummary summary) {
        return new CommentReactionResponse(
            summary.type(),
            emoji(summary.type()),
            label(summary.type()),
            summary.count(),
            summary.reactedByCurrentUser()
        );
    }

    private static String emoji(CommentReactionType type) {
        return switch (type) {
            case LIKE -> "👍";
            case LOVE -> "❤️";
            case LAUGH -> "😂";
            case WOW -> "😮";
        };
    }

    private static String label(CommentReactionType type) {
        return switch (type) {
            case LIKE -> "Нравится";
            case LOVE -> "Люблю";
            case LAUGH -> "Смешно";
            case WOW -> "Удивительно";
        };
    }
}
