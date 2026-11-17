package com.example.bulgariantrainer.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class AoristAnswerNormalizer {
    private static final Pattern WHITESPACE=Pattern.compile("\\s+",Pattern.UNICODE_CHARACTER_CLASS);
    private AoristAnswerNormalizer() {}
    static String key(String text) {
        String nfc=Normalizer.normalize(text,Normalizer.Form.NFC).replace("\u0301","").replace("\u0300","");
        return Normalizer.normalize(WHITESPACE.matcher(nfc).replaceAll(" ").strip().toLowerCase(Locale.ROOT),Normalizer.Form.NFC);
    }
}
