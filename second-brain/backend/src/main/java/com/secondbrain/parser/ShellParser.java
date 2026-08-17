package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class ShellParser extends BaseLanguageParser {

    private static final Pattern FUNCTION = Pattern.compile(
        "^(?:(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\(\\s*\\)|" +
        "function\\s+(?<name2>[A-Za-z_][A-Za-z0-9_]*)|" +
        "function\\s+(?<name3>[A-Za-z_][A-Za-z0-9_]*)\\s*\\(\\s*\\))" +
        "\\s*\\{?",
        Pattern.MULTILINE
    );

    private static final Pattern VARIABLE = Pattern.compile(
        "^(?<name>[A-Z][A-Z0-9_]+)\\s*=",
        Pattern.MULTILINE
    );

    private static final Pattern SOURCE = Pattern.compile(
        "^\\s*(?:source|\\.)\\s+['\"]?(?<module>[^'\"\\s]+)['\"]?",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return null; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return SOURCE; }

    @Override
    protected Pattern constantPattern() { return VARIABLE; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".sh", ".bash", ".zsh", ".fish", ".ksh", ".csh");
    }

    @Override
    public String languageName() { return "Shell"; }
}
