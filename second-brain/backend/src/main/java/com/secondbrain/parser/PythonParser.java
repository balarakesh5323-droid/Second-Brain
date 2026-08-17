package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class PythonParser extends BaseLanguageParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*class\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\((?<parent>[^)]+)\\))?\\s*:",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:async\\s+)?def\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\((?<params>[^)]*)\\)(?:\\s*->\\s*(?<returnType>[^:]+))?\\s*:",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*(?:from\\s+(?<module>[A-Za-z_][A-Za-z0-9_.]*)\\s+)?import\\s+(?<names>[A-Za-z_*, ][A-Za-z0-9_*, ]*)",
        Pattern.MULTILINE
    );

    private static final Pattern DECORATOR = Pattern.compile(
        "^\\s*@(?<name>[A-Za-z_][A-Za-z0-9_.]*)",
        Pattern.MULTILINE
    );

    private static final Pattern CONSTANT = Pattern.compile(
        "^\\s*(?<name>[A-Z][A-Z0-9_]+)\\s*=\\s*(?<value>.+)",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return CLASS; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return IMPORT; }

    @Override
    protected Pattern decoratorPattern() { return DECORATOR; }

    @Override
    protected Pattern constantPattern() { return CONSTANT; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".py", ".pyi", ".pyx");
    }

    @Override
    public String languageName() { return "Python"; }
}
