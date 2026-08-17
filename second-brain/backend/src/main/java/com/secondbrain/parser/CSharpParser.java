package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class CSharpParser extends BaseLanguageParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*(?:public|private|internal|protected)?\\s*(?:abstract\\s+|static\\s+|sealed\\s+|partial\\s+)*class\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern INTERFACE = Pattern.compile(
        "^\\s*(?:public|private|internal|protected)?\\s*(?:partial\\s+)?interface\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern STRUCT = Pattern.compile(
        "^\\s*(?:public|private|internal|protected)?\\s*(?:readonly\\s+|ref\\s+|partial\\s+)*struct\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern ENUM = Pattern.compile(
        "^\\s*(?:public|private|internal|protected)?\\s*enum\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*:\\s*(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:(?:public|private|internal|protected|static|async|virtual|override|abstract|sealed|new)\\s+)*" +
        "(?:[A-Za-z_][A-Za-z0-9_<>?,\\[\\] ]*\\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "\\s*\\((?<params>[^)]*)\\)" +
        "(?:\\s*(?:where\\s+[A-Za-z_][A-Za-z0-9_<>\\s:]+))?" +
        "\\s*(?:=>|\\{|;)",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*using\\s+(?<module>[A-Za-z_][A-Za-z0-9_.]*)\\s*;",
        Pattern.MULTILINE
    );

    private static final Pattern NAMESPACE = Pattern.compile(
        "^\\s*namespace\\s+(?<name>[A-Za-z_][A-Za-z0-9_.]*)\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern DELEGATE = Pattern.compile(
        "^\\s*(?:public|private|internal|protected)?\\s*(?:delegate|event)\\s+(?:[A-Za-z_][A-Za-z0-9_<>?,\\[\\]]*\\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "\\s*\\((?<params>[^)]*)\\)",
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
    protected Pattern structPattern() { return STRUCT; }

    @Override
    protected Pattern enumPattern() { return ENUM; }

    @Override
    protected Pattern modulePattern() { return NAMESPACE; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".cs");
    }

    @Override
    public String languageName() { return "C#"; }
}
