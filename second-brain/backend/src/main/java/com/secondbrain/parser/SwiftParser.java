package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class SwiftParser extends BaseLanguageParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*(?:public|private|internal|open|final)?\\s*class\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern STRUCT = Pattern.compile(
        "^\\s*(?:public|private|internal)?\\s*struct\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern PROTOCOL = Pattern.compile(
        "^\\s*(?:public|private|internal)?\\s*protocol\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern ENUM = Pattern.compile(
        "^\\s*(?:public|private|internal)?\\s*enum\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:(?:public|private|internal|open|final|static|class|mutating|throws|async)\\s+)*func\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "\\s*\\((?<params>[^)]*)\\)" +
        "(?:\\s*(?:throws|async))?\\s*(?:->\\s*(?<returnType>[^\\s{]+))?",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*import\\s+(?<module>[A-Za-z_][A-Za-z0-9_.]*)\\s*$",
        Pattern.MULTILINE
    );

    private static final Pattern EXTENSION = Pattern.compile(
        "^\\s*extension\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return CLASS; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return IMPORT; }

    @Override
    protected Pattern protocolPattern() { return PROTOCOL; }

    @Override
    protected Pattern structPattern() { return STRUCT; }

    @Override
    protected Pattern enumPattern() { return ENUM; }

    @Override
    protected Pattern typeAliasPattern() { return EXTENSION; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".swift");
    }

    @Override
    public String languageName() { return "Swift"; }
}
