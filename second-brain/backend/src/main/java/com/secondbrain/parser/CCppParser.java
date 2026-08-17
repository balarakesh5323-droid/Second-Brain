package com.secondbrain.parser;

import java.util.Set;
import java.util.regex.Pattern;

public class CCppParser extends BaseLanguageParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*(?:class|struct)\\s+(?:\\w+\\s+)*(?<name>[A-Za-z_][A-Za-z0-9_]*)" +
        "(?:\\s*:\\s*(?:public|private|protected)\\s+(?<parent>[A-Za-z_][A-Za-z0-9_]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^(?:\\s*(?:static|inline|extern|virtual|const)\\s+)*" +
        "(?<returnType>[A-Za-z_][A-Za-z0-9_\\s\\*&<>]*)\\s+" +
        "(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\((?<params>[^)]*)\\)" +
        "(?:\\s*const)?(?:\\s*override)?(?:\\s*=\\s*0)?\\s*[;{]",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*#\\s*include\\s+[<\"](?<module>[^>\"]+)[>\"]",
        Pattern.MULTILINE
    );

    private static final Pattern NAMESPACE = Pattern.compile(
        "^\\s*namespace\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern ENUM = Pattern.compile(
        "^\\s*(?:enum\\s+(?:class\\s+)?)(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern TYPEDEF = Pattern.compile(
        "^\\s*typedef\\s+(?<value>.+?)\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*;",
        Pattern.MULTILINE
    );

    private static final Pattern MACRO = Pattern.compile(
        "^\\s*#\\s*define\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\((?<params>[^)]*)\\))?\\s*(?<value>.*)",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return CLASS; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return IMPORT; }

    @Override
    protected Pattern enumPattern() { return ENUM; }

    @Override
    protected Pattern typeAliasPattern() { return TYPEDEF; }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".c", ".cpp", ".cc", ".cxx", ".h", ".hpp", ".hxx", ".hh");
    }

    @Override
    public String languageName() { return "C/C++"; }
}
