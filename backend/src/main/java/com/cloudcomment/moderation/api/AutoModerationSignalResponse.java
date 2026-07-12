package com.cloudcomment.moderation.api;

import com.cloudcomment.automoderation.domain.AutoModerationSignalSnapshot;

record AutoModerationSignalResponse(
    String code,
    int score,
    String message
) {

    static AutoModerationSignalResponse from(AutoModerationSignalSnapshot signal) {
        return new AutoModerationSignalResponse(signal.code(), signal.score(), message(signal.code()));
    }

    private static String message(String code) {
        return switch (code) {
            case "CUSTOM_BLOCKED_WORD" -> "Найдено стоп-слово владельца";
            case "BLOCKED_LINK" -> "Ссылки запрещены политикой";
            case "LINK_FLOOD" -> "Превышен допустимый лимит ссылок";
            case "CONTAINS_LINK" -> "Комментарий содержит ссылку";
            case "SPAM_PHRASE" -> "Найден спам-маркер";
            case "TOXICITY" -> "Найден токсичный маркер";
            case "REPEATED_CHARACTERS" -> "Много повторяющихся символов";
            case "OBFUSCATION" -> "Обнаружена подозрительная обфускация текста";
            case "AGGRESSIVE_CAPS" -> "Слишком много текста в верхнем регистре";
            case "SUSPICIOUS_CONTACT" -> "Обнаружен подозрительный контакт";
            default -> "Обнаружен сигнал автомодерации";
        };
    }
}
