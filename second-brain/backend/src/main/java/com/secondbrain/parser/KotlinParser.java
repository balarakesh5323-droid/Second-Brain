package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class KotlinParser extends BaseLanguageParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*(?:abstract\\s+|open\\s+|data\\s+|sealed\\s+|inner\\s+|enum\\s+|value\\s+)?class\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*\\([^)]*\\))?" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*(?:\\{|$)",
        Pattern.MULTILINE
    );

    private static final Pattern INTERFACE = Pattern.compile(
        "^\\s*(?:interface|annotation class)\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*(?:\\{|$)",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:(?:override|open|abstract|inline|suspend|private|protected|public|internal)\\s+)*fun\\s+(?:<[^>]+>\\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*(?:<[^>]+>)?\\s*\\((?<params>[^)]*)\\)(?:\\s*:\\s*(?<returnType>[^\\s{]+))?",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*import\\s+(?<module>[A-Za-z_][A-Za-z0-9_.]*)\\s*$",
        Pattern.MULTILINE
    );

    private static final Pattern OBJECT = Pattern.compile(
        "^\\s*(?:object|companion object)\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*(?:\\{|$)",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return CLASS; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return IMPORT; }

    @Override
    protected Pattern interfacePattern() { return INTERFACE; }

    @Override
    protected Pattern modulePattern() { return OBJECT; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".kt", ".kts");
    }

    @Override
    public String languageName() { return "Kotlin"; }
}
