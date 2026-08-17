package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class GoParser extends BaseLanguageParser {

    private static final Pattern STRUCT = Pattern.compile(
        "^\\s*type\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s+struct\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern INTERFACE = Pattern.compile(
        "^\\s*type\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s+interface\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:func\\s+)?(?:\\([^)]+\\)\\s+)?func\\s+(?:\\*?(?<receiver>[A-Za-z_][A-Za-z0-9_]*)\\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\((?<params>[^)]*)\\)(?:\\s*(?<returnType>[^\\s{]+))?",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*(?:import\\s+(?:\\((?<multiline>[\\s\\S]*?)\\)|\"(?<single>[^\"]+)\"))",
        Pattern.MULTILINE
    );

    private static final Pattern CONST = Pattern.compile(
        "^\\s*(?:const\\s+)(?<name>[A-Z][A-Z0-9_]+)\\s*(?:=.*)?",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return STRUCT; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return IMPORT; }

    @Override
    protected Pattern interfacePattern() { return INTERFACE; }

    @Override
    protected Pattern constantPattern() { return CONST; }

    @Override
    protected Pattern structPattern() { return STRUCT; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".go");
    }

    @Override
    public String languageName() { return "Go"; }
}
