package com.cloudcomment.comment.api;

import com.cloudcomment.comment.domain.CommentAuthor;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommentAuthorResponseTests {

    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void publicDisplayNameFallsBackForMissingOrBlankNames() {
        assertThat(CommentAuthorResponse.publicDisplayName(author(null))).isEqualTo("Участник");
        assertThat(CommentAuthorResponse.publicDisplayName(author("   "))).isEqualTo("Участник");
    }

    @Test
    void publicDisplayNameNeverReturnsEmailLikeValues() {
        assertThat(CommentAuthorResponse.publicDisplayName(author("visitor@example.com"))).isEqualTo("Участник");
        assertThat(CommentAuthorResponse.publicDisplayName(author("VISITOR@EXAMPLE.COM"))).isEqualTo("Участник");
        assertThat(CommentAuthorResponse.publicDisplayName(author("contact@site.test"))).isEqualTo("Участник");
    }

    @Test
    void publicDisplayNamePreservesTrimmedCyrillicName() {
        assertThat(CommentAuthorResponse.publicDisplayName(author("  Анна  "))).isEqualTo("Анна");
    }

    @Test
    void responseContainsOnlyPublicAuthorFields() {
        CommentAuthorResponse response = CommentAuthorResponse.from(author("Анна"));

        assertThat(response.id()).isEqualTo(AUTHOR_ID);
        assertThat(response.displayName()).isEqualTo("Анна");
    }

    private CommentAuthor author(String displayName) {
        return new CommentAuthor(AUTHOR_ID, "visitor@example.com", displayName);
    }
}
