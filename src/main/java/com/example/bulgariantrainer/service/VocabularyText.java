package com.example.bulgariantrainer.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class VocabularyText {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private VocabularyText() {
    }

    static String clean(String text) {
        return WHITESPACE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll(" ").strip();
    }

    static String key(String text) {
        return clean(text).toLowerCase(Locale.ROOT);
    }

    static String optional(String text) {
        if (text == null) return null;
        String cleaned = clean(text);
        return cleaned.isEmpty() ? null : cleaned;
    }

    static String searchPattern(String query) {
        return "%" + key(query).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
