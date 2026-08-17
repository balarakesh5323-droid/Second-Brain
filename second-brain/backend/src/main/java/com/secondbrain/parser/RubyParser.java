package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class RubyParser extends BaseLanguageParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*(?:class|class\\s*<<\\s*self)\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*<\\s*(?<parent>[A-Za-z_][A-Za-z0-9_]*))?" +
        "\\s*(?:#|$|;|\\{)",
        Pattern.MULTILINE
    );

    private static final Pattern MODULE = Pattern.compile(
        "^\\s*module\\s+(?<name>[A-Za-z_][A-Za-z0-9_:]*)",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:(?:def\\s+|defself\\.)(?<name>[A-Za-z_][A-Za-z0-9_!?]*)\\s*(?:\\((?<params>[^)]*)\\))?)" +
        "|(?:(?<receiver>[A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\s*def\\s+(?<name2>[A-Za-z_][A-Za-z0-9_!?]*)\\s*(?:\\((?<params2>[^)]*)\\))?)" +
        "|(?:(?<receiver2>[A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\s*defself\\s+(?<name3>[A-Za-z_][A-Za-z0-9_!?]*)\\s*(?:\\((?<params3>[^)]*)\\))?)",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*(?:require\\s+['\"](?<module>[^'\"]+)['\"]|" +
        "require_relative\\s+['\"](?<module2>[^'\"]+)['\"]|" +
        "load\\s+['\"](?<module3>[^'\"]+)['\"])",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return CLASS; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return IMPORT; }

    @Override
    protected Pattern modulePattern() { return MODULE; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".rb", ".rake", ".gemspec", ".ru");
    }

    @Override
    public String languageName() { return "Ruby"; }
}
