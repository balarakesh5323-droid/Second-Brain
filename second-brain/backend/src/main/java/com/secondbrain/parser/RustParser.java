package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class RustParser extends BaseLanguageParser {

    private static final Pattern STRUCT = Pattern.compile(
        "^\\s*(?:pub\\s+)?(?:\\s*#\\[.*?\\]\\s*)*struct\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "(?:\\s*\\{)",
        Pattern.MULTILINE
    );

    private static final Pattern ENUM = Pattern.compile(
        "^\\s*(?:pub\\s+)?enum\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern TRAIT = Pattern.compile(
        "^\\s*(?:pub\\s+)?trait\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "\\s*(?:\\{|:)",
        Pattern.MULTILINE
    );

    private static final Pattern IMPL = Pattern.compile(
        "^\\s*(?:pub\\s+)?impl(?:\\s*<[^>]+>)?\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "\\s*(?:\\{|for)",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:(?:pub|pub\\(crate\\)|pub\\(super\\))\\s+)?(?:\\s*(?:unsafe|async|const|extern\\s+\"C\")\\s+)*fn\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<[^>]+>)?" +
        "\\s*\\((?<params>[^)]*)\\)" +
        "(?:\\s*->\\s*(?<returnType>[^\\s{]+))?",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*(?:use\\s+(?<module>[A-Za-z_][A-Za-z0-9_:]*)\\s*;|" +
        "mod\\s+(?<modName>[A-Za-z_][A-Za-z0-9_]*)\\s*;|" +
        "extern\\s+crate\\s+(?<crate>[A-Za-z_][A-Za-z0-9_]*)\\s*;)",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return STRUCT; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return IMPORT; }

    @Override
    protected Pattern structPattern() { return STRUCT; }

    @Override
    protected Pattern enumPattern() { return ENUM; }

    @Override
    protected Pattern traitPattern() { return TRAIT; }

    @Override
    protected Pattern implPattern() { return IMPL; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".rs");
    }

    @Override
    public String languageName() { return "Rust"; }
}
