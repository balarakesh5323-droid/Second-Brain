package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class ScalaParser extends BaseLanguageParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*(?:abstract\\s+|final\\s+|sealed\\s+|implicit\\s+|lazy\\s+)*class\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*\\([^)]*\\))?" +
        "(?:\\s*(?:extends|with)\\s+(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*(?:\\{|with|$)",
        Pattern.MULTILINE
    );

    private static final Pattern TRAIT = Pattern.compile(
        "^\\s*(?:abstract\\s+|sealed\\s+)*trait\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*(?:extends|with)\\s+(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*(?:\\{|with|$)",
        Pattern.MULTILINE
    );

    private static final Pattern OBJECT = Pattern.compile(
        "^\\s*(?:case\\s+)?object\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*(?:extends|with)\\s+(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*))?" +
        "\\s*(?:\\{|with|$)",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:(?:override|abstract|final|implicit|private|protected|public)\\s+)*def\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "\\s*(?:\\((?<params>[^)]*)\\))(?:\\s*:\\s*(?<returnType>[^\\s{=]+))?",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*import\\s+(?<module>[A-Za-z_][A-Za-z0-9_.]*(?:\\.[*_])?)\\s*$",
        Pattern.MULTILINE
    );

    private static final Pattern TYPE_ALIAS = Pattern.compile(
        "^\\s*(?:type|class)\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "\\s*=\\s*(?<value>.+)",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return CLASS; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return IMPORT; }

    @Override
    protected Pattern traitPattern() { return TRAIT; }

    @Override
    protected Pattern modulePattern() { return OBJECT; }

    @Override
    protected Pattern typeAliasPattern() { return TYPE_ALIAS; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".scala", ".sc");
    }

    @Override
    public String languageName() { return "Scala"; }
}
