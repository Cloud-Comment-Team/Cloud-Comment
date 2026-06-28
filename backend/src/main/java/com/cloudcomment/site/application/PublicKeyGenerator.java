package com.cloudcomment.site.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
class PublicKeyGenerator {

    private static final int KEY_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    String generate() {
        byte[] bytes = new byte[KEY_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
