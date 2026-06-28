package com.cloudcomment.site.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicKeyGeneratorTests {

    @Test
    void generateReturnsLowercaseShaLikeHexKey() {
        assertThat(new PublicKeyGenerator().generate()).matches("[0-9a-f]{64}");
    }
}
