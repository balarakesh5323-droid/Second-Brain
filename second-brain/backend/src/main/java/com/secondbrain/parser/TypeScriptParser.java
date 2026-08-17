package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class TypeScriptParser extends JavaScriptParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*(?:export\\s+)?(?:abstract\\s+)?class\\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)" +
        "(?:\\s+extends\\s+(?<parent>[A-Za-z_$][A-Za-z0-9_$]*))?" +
        "(?:\\s+implements\\s+(?<interfaces>[A-Za-z_$][A-Za-z0-9_$, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern INTERFACE = Pattern.compile(
        "^\\s*(?:export\\s+)?(?:declare\\s+)?interface\\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)" +
        "(?:\\s+extends\\s+(?<parent>[A-Za-z_$][A-Za-z0-9_$, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern TYPE_ALIAS = Pattern.compile(
        "^\\s*(?:export\\s+)?type\\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:<[^>]+>)?\\s*=\\s*(?<value>.+)",
        Pattern.MULTILINE
    );

    private static final Pattern ENUM = Pattern.compile(
        "^\\s*(?:export\\s+)?(?:const\\s+)?enum\\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\\s*\\{",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return CLASS; }

    @Override
    protected Pattern interfacePattern() { return INTERFACE; }

    @Override
    protected Pattern typeAliasPattern() { return TYPE_ALIAS; }

    @Override
    protected Pattern enumPattern() { return ENUM; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".ts", ".tsx", ".mts", ".cts");
    }

    @Override
    public String languageName() { return "TypeScript"; }
}
