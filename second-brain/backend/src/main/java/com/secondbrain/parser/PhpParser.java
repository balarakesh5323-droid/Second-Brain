package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class PhpParser extends BaseLanguageParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*(?:abstract\\s+)?(?:final\\s+)?class\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s+extends\\s+(?<parent>[A-Za-z_\\\\][A-Za-z0-9_\\\\]*))?" +
        "(?:\\s+implements\\s+(?<interfaces>[A-Za-z_\\\\][A-Za-z0-9_\\\\, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern INTERFACE = Pattern.compile(
        "^\\s*interface\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s+extends\\s+(?<parent>[A-Za-z_\\\\][A-Za-z0-9_\\\\, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:(?:public|protected|private|static)\\s+)*function\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\((?<params>[^)]*)\\)" +
        "(?:\\s*:\\s*(?<returnType>[^\\s{]+))?",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*(?:use\\s+(?<namespace>[A-Za-z_\\\\][A-Za-z0-9_\\\\]*(?:\\\\\\*)?)\\s*;|" +
        "require(?:_once)?\\s+['\"](?<file>[^'\"]+)['\"];|" +
        "include(?:_once)?\\s+['\"](?<file2>[^'\"]+)['\"];)",
        Pattern.MULTILINE
    );

    private static final Pattern TRAIT = Pattern.compile(
        "^\\s*trait\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern ENUM = Pattern.compile(
        "^\\s*(?:enum\\s+)(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*(?::\\s*\\w+)?\\s*\\{",
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
    protected Pattern traitPattern() { return TRAIT; }

    @Override
    protected Pattern enumPattern() { return ENUM; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".php", ".phtml", ".php3", ".php4", ".php5", ".php7", ".php8");
    }

    @Override
    public String languageName() { return "PHP"; }
}
