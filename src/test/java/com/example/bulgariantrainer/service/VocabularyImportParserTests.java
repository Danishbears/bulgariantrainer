package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.exception.ImportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class VocabularyImportParserTests {
    private final VocabularyImportParser parser = new VocabularyImportParser();

    @ParameterizedTest
    @ValueSource(strings = {"куче - собака", "куче = собака", "куче – собака", "куче — собака", "куче\t\tсобака", "куче- собака"})
    void supportsObservedSeparators(String source) {
        var row = parse("words.txt", source).getFirst();
        assertThat(row.bulgarianText()).isEqualTo("куче");
        assertThat(row.russianText()).isEqualTo("собака");
        assertThat(row.status()).isEqualTo("READY");
        assertThat(row.sourceLine()).isEqualTo(source);
    }

    @Test
    void preservesHyphenatedMultiwordExpressionsAndCommas() {
        var rows = parse("words.txt", "горе-долу - более-менее\nна колко години си - сколько тебе лет\nотивам - ехать, идти");
        assertThat(rows.get(0).bulgarianText()).isEqualTo("горе-долу");
        assertThat(rows.get(0).russianText()).isEqualTo("более-менее");
        assertThat(rows.get(1).bulgarianText()).isEqualTo("на колко години си");
        assertThat(rows.get(2).russianText()).isEqualTo("ехать, идти");
        assertThat(rows.get(2).warnings()).contains("COMMA_OR_LIST");
    }

    @Test
    void flagsAmbiguitiesFoundInPersonalDocumentWithoutReversingOrSplitting() {
        String source = "hi - здрасти\nсакO - пиджак\n100% - винаги\nяке/якета - куртка/куртки\n"
                + "на едро - оптовые продажи \\ едър - крупный\nмомче - парень (средний род)\n"
                + "огледало - зеркало !!-1\nза́вчера, о́ня ден  \tпозавчера";
        var rows = parse("personal.txt", source);
        assertThat(rows.get(0).bulgarianText()).isEqualTo("hi");
        assertThat(rows.get(0).russianText()).isEqualTo("здрасти");
        assertThat(rows.get(0).warnings()).contains("LANGUAGE_REVIEW");
        assertThat(rows.get(1).warnings()).contains("LANGUAGE_REVIEW");
        assertThat(rows.get(2).warnings()).contains("LANGUAGE_REVIEW");
        assertThat(rows.get(3).bulgarianText()).isEqualTo("яке/якета");
        assertThat(rows.get(3).warnings()).contains("ALTERNATIVES");
        assertThat(rows.get(4).russianText()).isEqualTo("оптовые продажи \\ едър - крупный");
        assertThat(rows.get(4).warnings()).contains("MULTIPLE_SEPARATORS");
        assertThat(rows.get(5).warnings()).contains("ANNOTATION");
        assertThat(rows.get(6).russianText()).isEqualTo("зеркало !!-1");
        assertThat(rows.get(7).russianText()).isEqualTo("позавчера");
    }

    @Test
    void retainsEmptyInvalidAndUntranslatedLinesWithLineNumbers() {
        var rows = parse("words.txt", "части на тялото\r\n\r\nлитър - \r\nкуче - собака\r\n");
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).status()).isEqualTo("INVALID");
        assertThat(rows.get(0).sourceLine()).isEqualTo("части на тялото");
        assertThat(rows.get(1).status()).isEqualTo("EMPTY");
        assertThat(rows.get(2).warnings()).contains("MISSING_VALUE");
        assertThat(rows.get(3).lineNumber()).isEqualTo(4);
        assertThat(parse("words.txt", "")).hasSize(1);
        assertThat(parse("words.txt", "").getFirst().status()).isEqualTo("EMPTY");
    }

    @Test
    void acceptsUtf8BomAndRejectsMalformedBytesWithoutReplacementCharacters() {
        var rows = parse("WORDS.TXT", "\uFEFFкуче - собака");
        assertThat(rows.getFirst().bulgarianText()).isEqualTo("куче");
        assertThatThrownBy(() -> parser.parse("words.txt", new byte[] {(byte) 0xD0, 0x20}))
                .isInstanceOfSatisfying(ImportException.class, error -> assertThat(error.getCode()).isEqualTo("INVALID_UTF8"));
        assertThat(parse("words.txt", "куче - соб\u0000ака").getFirst().warnings()).contains("CONTROL_CHARACTER");
    }

    @Test
    void csvSupportsBomQuotedCommasEscapedQuotesAndOptionalPartOfSpeech() {
        var rows = parse("words.csv", "\uFEFFbulgarian,russian,partOfSpeech\r\nотивам,\"ехать, идти\",verb\r\n"
                + "дума,\"слово \"\"цитата\"\"\",noun\r\n");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).status()).isEqualTo("HEADER");
        assertThat(rows.get(1).russianText()).isEqualTo("ехать, идти");
        assertThat(rows.get(1).partOfSpeech()).isEqualTo("verb");
        assertThat(rows.get(2).russianText()).isEqualTo("слово \"цитата\"");
        assertThat(parse("words.csv", "russian,bulgarian\nсобака,куче").get(1).bulgarianText()).isEqualTo("куче");
    }

    @Test
    void csvRetainsMultilineSourceAndRecoversAfterMalformedRecord() {
        var rows = parse("words.csv", "bulgarian,russian\n\"добро\nутро\",\"доброе утро\"\n"
                + "куче,\"собака\"broken\nкотка,кошка\n");
        assertThat(rows).hasSize(4);
        assertThat(rows.get(1).lineNumber()).isEqualTo(2);
        assertThat(rows.get(1).endLineNumber()).isEqualTo(3);
        assertThat(rows.get(1).sourceLine()).isEqualTo("\"добро\nутро\",\"доброе утро\"");
        assertThat(rows.get(1).bulgarianText()).isEqualTo("добро утро");
        assertThat(rows.get(1).warnings()).contains("MULTILINE_VALUE");
        assertThat(rows.get(2).status()).isEqualTo("INVALID");
        assertThat(rows.get(3).bulgarianText()).isEqualTo("котка");
        assertThat(rows.get(3).lineNumber()).isEqualTo(5);
    }

    @Test
    void malformedCsvNeverDiscardsUnclosedRecordsOrGuessesHeaders() {
        var unclosed = parse("words.csv", "bulgarian,russian\nкуче,\"собака\nкотка,кошка");
        assertThat(unclosed).hasSize(2);
        assertThat(unclosed.get(1).warnings()).contains("CSV_SYNTAX");
        assertThat(unclosed.get(1).sourceLine()).isEqualTo("куче,\"собака\nкотка,кошка");
        var missing = parse("words.csv", "куче,собака\nкотка,кошка");
        assertThat(missing).allMatch(row -> row.status().equals("INVALID"));
        assertThat(parse("words.csv", "bulgarian,russian\nотивам,идти,ехать").get(1).warnings())
                .contains("CSV_COLUMN_COUNT");
        assertThat(parse("words.csv", "bulgarian;russian\nкуче;собака").getFirst().warnings())
                .contains("UNSUPPORTED_CSV_HEADER");
        assertThat(parse("words.csv", "bulgarian,russian,notes\nкуче,собака,заметка").get(1).status())
                .isEqualTo("INVALID");
        assertThat(parse("words.txt", "куче,собака").getFirst().status()).isEqualTo("INVALID");
    }

    @Test
    void rejectsOversizedUnsupportedFilesAndTooManyRecords() {
        assertThatThrownBy(() -> parser.parse("words.txt", new byte[VocabularyImportParser.MAX_BYTES + 1]))
                .isInstanceOfSatisfying(ImportException.class, error -> assertThat(error.getStatus()).isEqualTo(413));
        assertThatThrownBy(() -> parse("words.pdf", "куче - собака")).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> parse("words.txt", "куче - собака\n".repeat(2001))).isInstanceOf(ImportException.class);
        assertThat(parse("words.txt", "а".repeat(256) + " - перевод").getFirst().status()).isEqualTo("INVALID");
    }

    private java.util.List<VocabularyImportParser.ParsedEntry> parse(String name, String content) {
        return parser.parse(name, content.getBytes(StandardCharsets.UTF_8));
    }
}
