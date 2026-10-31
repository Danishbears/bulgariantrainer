package com.example.bulgariantrainer.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class AspectAnswerNormalizer {
    private static final Pattern WHITESPACE=Pattern.compile("\\s+",Pattern.UNICODE_CHARACTER_CLASS);
    private AspectAnswerNormalizer() {}
    static String key(String value) {
        String nfc=Normalizer.normalize(value,Normalizer.Form.NFC).replace("\u0301","").replace("\u0300","");
        return Normalizer.normalize(WHITESPACE.matcher(nfc).replaceAll(" ").strip().toLowerCase(Locale.ROOT),Normalizer.Form.NFC);
    }
}
