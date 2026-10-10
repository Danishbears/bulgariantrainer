package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.exception.ImportException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class VocabularyImportParser {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_ENTRIES = 2000;
    private static final Pattern PAIR_SEPARATOR = Pattern.compile(
            "=|(?<=\\s)[-–—]|[-–—](?=\\s)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern TABS = Pattern.compile("\\h*\\t\\h*");
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");
    private static final Pattern LATIN = Pattern.compile("\\p{IsLatin}");

    public List<ParsedEntry> parse(String filename, byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new ImportException(413, "FILE_TOO_LARGE", "Maximum file size is 1 MiB");
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".txt") && !lowerName.endsWith(".csv")) {
            throw new ImportException(415, "UNSUPPORTED_FILE", "Only TXT and CSV files are supported");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new ImportException(400, "INVALID_UTF8", "The file must use UTF-8 encoding");
        }
        // A leading BOM is encoding metadata, not part of the first vocabulary term.
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        return lowerName.endsWith(".csv") ? csv(text) : txt(text);
    }

    private List<ParsedEntry> txt(String text) {
        List<String> lines = physicalLines(text);
        checkCount(lines.size());
        List<ParsedEntry> result = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                result.add(entry(index + 1, index + 1, index + 1, line, "", "", null, "EMPTY", List.of("EMPTY_RECORD")));
                continue;
            }
            var matcher = PAIR_SEPARATOR.matcher(line);
            boolean found = matcher.find();
            if (!found) {
                matcher = TABS.matcher(line);
                found = matcher.find();
            }
            if (!found) {
                result.add(entry(index + 1, index + 1, index + 1, line, "", "", null, "INVALID", List.of("NO_SEPARATOR")));
                continue;
            }
            int start = matcher.start();
            int end = matcher.end();
            List<String> warnings = new ArrayList<>();
            if (matcher.find()) warnings.add("MULTIPLE_SEPARATORS");
            result.add(pair(index + 1, index + 1, index + 1, line, line.substring(0, start),
                    line.substring(end), null, warnings));
        }
        return List.copyOf(result);
    }

    private List<ParsedEntry> csv(String text) {
        List<CsvRecord> records = readCsv(text);
        List<ParsedEntry> result = new ArrayList<>();
        Map<String, Integer> columns = null;
        boolean headerSeen = false;
        for (CsvRecord record : records) {
            int id = result.size() + 1;
            if (record.source().isBlank()) {
                result.add(entry(id, record.startLine(), record.endLine(), record.source(), "", "", null,
                        "EMPTY", List.of("EMPTY_RECORD")));
                continue;
            }
            if (!headerSeen) {
                headerSeen = true;
                List<String> names = record.fields().stream().map(VocabularyText::key).toList();
                if (!record.invalid() && names.size() >= 2 && names.size() <= 3
                        && names.contains("bulgarian") && names.contains("russian")
                        && new HashSet<>(names).size() == names.size()
                        && names.stream().allMatch(Set.of("bulgarian", "russian", "partofspeech")::contains)) {
                    columns = new HashMap<>();
                    for (int i = 0; i < names.size(); i++) columns.put(names.get(i), i);
                    result.add(entry(id, record.startLine(), record.endLine(), record.source(), "", "", null,
                            "HEADER", List.of("CSV_HEADER")));
                } else {
                    result.add(entry(id, record.startLine(), record.endLine(), record.source(), "", "", null,
                            "INVALID", List.of("UNSUPPORTED_CSV_HEADER")));
                }
                continue;
            }
            if (columns == null || record.invalid() || record.fields().size() != columns.size()) {
                String warning = columns == null ? "UNSUPPORTED_CSV_HEADER"
                        : record.invalid() ? "CSV_SYNTAX" : "CSV_COLUMN_COUNT";
                result.add(entry(id, record.startLine(), record.endLine(), record.source(), "", "", null,
                        "INVALID", List.of(warning)));
                continue;
            }
            List<String> warnings = new ArrayList<>();
            if (record.startLine() != record.endLine()) warnings.add("MULTILINE_VALUE");
            result.add(pair(id, record.startLine(), record.endLine(), record.source(),
                    record.fields().get(columns.get("bulgarian")), record.fields().get(columns.get("russian")),
                    columns.containsKey("partofspeech") ? record.fields().get(columns.get("partofspeech")) : null, warnings));
        }
        return List.copyOf(result);
    }

    private ParsedEntry pair(int id, int firstLine, int lastLine, String source, String bg, String ru,
                             String part, List<String> warnings) {
        bg = VocabularyText.clean(bg);
        ru = VocabularyText.clean(ru);
        part = VocabularyText.optional(part);
        boolean invalid = false;
        if (bg.isEmpty() || ru.isEmpty()) { warnings.add("MISSING_VALUE"); invalid = true; }
        if (bg.length() > 255 || ru.length() > 255 || (part != null && part.length() > 100)) {
            warnings.add("VALUE_TOO_LONG"); invalid = true;
        }
        if (source.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r')) {
            warnings.add("CONTROL_CHARACTER"); invalid = true;
        }
        if (!bg.isEmpty() && !ru.isEmpty()) {
            if (!CYRILLIC.matcher(bg).find() || !CYRILLIC.matcher(ru).find()
                    || LATIN.matcher(bg).find() || LATIN.matcher(ru).find()) warnings.add("LANGUAGE_REVIEW");
            if (bg.contains("/") || ru.contains("/") || bg.contains("\\") || ru.contains("\\")) warnings.add("ALTERNATIVES");
            if (bg.contains(",") || ru.contains(",") || ru.endsWith(";")) warnings.add("COMMA_OR_LIST");
            if (source.contains("(") || source.contains(")") || source.contains("!!")) warnings.add("ANNOTATION");
        }
        return entry(id, firstLine, lastLine, source, bg, ru, part,
                invalid ? "INVALID" : warnings.isEmpty() ? "READY" : "WARNING", warnings);
    }

    private ParsedEntry entry(int id, int first, int last, String source, String bg, String ru,
                              String part, String status, List<String> warnings) {
        return new ParsedEntry(id, first, last, source, bg, ru, part, status, List.copyOf(warnings));
    }

    private List<String> physicalLines(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\r\\n|\\n|\\r", -1)));
        // A terminal line ending terminates the last line; it does not create another empty record.
        if (lines.size() > 1 && lines.getLast().isEmpty()) lines.removeLast();
        return lines;
    }

    private void checkCount(int size) {
        if (size > MAX_ENTRIES) throw new ImportException(400, "TOO_MANY_ENTRIES", "Maximum record count is 2000");
    }

    /**
     * Strict comma CSV: quoted delimiters, escaped quotes and multiline fields are supported.
     * A malformed record remains visible, with its entire source range; it is never dropped.
     */
    List<CsvRecord> readCsv(String text) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int state = 0; // 0: field start, 1: unquoted, 2: quoted, 3: after closing quote
        int start = 0, line = 1, startLine = 1;
        boolean invalid = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean newline = c == '\r' || c == '\n';
            int newlineLength = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? 2 : 1;
            if (state == 2) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else state = 3;
                } else if (newline) {
                    field.append(text, i, i + newlineLength);
                    line++;
                    i += newlineLength - 1;
                } else field.append(c);
                continue;
            }
            if (newline) {
                fields.add(field.toString());
                records.add(new CsvRecord(startLine, line, text.substring(start, i), List.copyOf(fields), invalid));
                checkCount(records.size());
                i += newlineLength - 1;
                line++;
                startLine = line;
                start = i + 1;
                fields.clear();
                field.setLength(0);
                state = 0;
                invalid = false;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
                state = 0;
            } else if (c == '"' && state == 0) {
                state = 2;
            } else if (state == 3) {
                if (c != ' ' && c != '\t') invalid = true;
            } else {
                if (c == '"') invalid = true;
                field.append(c);
                state = 1;
            }
        }
        if (start < text.length() || records.isEmpty()) {
            fields.add(field.toString());
            records.add(new CsvRecord(startLine, line, text.substring(start), List.copyOf(fields), invalid || state == 2));
            checkCount(records.size());
        }
        return records;
    }

    public record ParsedEntry(int rowId, int lineNumber, int endLineNumber, String sourceLine,
                              String bulgarianText, String russianText, String partOfSpeech,
                              String status, List<String> warnings) {
    }

    record CsvRecord(int startLine, int endLine, String source, List<String> fields, boolean invalid) {
    }
}

