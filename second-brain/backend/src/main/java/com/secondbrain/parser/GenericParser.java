package com.secondbrain.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenericParser implements LanguageParser {

    private static final Pattern GENERIC_FUNCTION = Pattern.compile(
        "^(?:[a-zA-Z_][a-zA-Z0-9_<>\\[\\], ]*\\s+)?(?<name>[a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:<[^>]+>)?\\s*\\((?<params>[^)]*)\\)" +
        "(?:\\s*(?:->|=>|:)\\s*(?<returnType>[^\\s{]+))?",
        Pattern.MULTILINE
    );

    private static final Pattern GENERIC_CLASS = Pattern.compile(
        "(?:class|struct|interface|type|record|data\\s+class|enum|trait|protocol|module|namespace)\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)(?:\\s*<[^>]+>)?(?:\\s*(?:extends|implements|inherits|with|derives|:\\s*))?(?<parent>[A-Za-z_][A-Za-z0-9_<>?, ]*)?",
        Pattern.MULTILINE
    );

    @Override
    public Map<String, Object> parse(String filePath, String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("file", filePath);
        result.put("language", detectLanguageFromPath(filePath));

        List<Map<String, Object>> classes = new ArrayList<>();
        Matcher classMatcher = GENERIC_CLASS.matcher(content);
        while (classMatcher.find()) {
            Map<String, Object> classInfo = new HashMap<>();
            classInfo.put("name", classMatcher.group("name"));
            classInfo.put("type", "class");
            if (classMatcher.group("parent") != null && !classMatcher.group("parent").isBlank()) {
                classInfo.put("extends", List.of(classMatcher.group("parent").trim()));
            }
            classes.add(classInfo);
        }

        List<Map<String, Object>> functions = new ArrayList<>();
        Matcher funcMatcher = GENERIC_FUNCTION.matcher(content);
        while (funcMatcher.find()) {
            Map<String, Object> funcInfo = new HashMap<>();
            funcInfo.put("name", funcMatcher.group("name"));
            if (funcMatcher.group("params") != null) {
                funcInfo.put("parameters", funcMatcher.group("params").trim());
            }
            if (funcMatcher.group("returnType") != null) {
                funcInfo.put("returnType", funcMatcher.group("returnType").trim());
            }
            functions.add(funcInfo);
        }

        result.put("classes", classes);
        result.put("functions", functions);
        result.put("imports", new ArrayList<>());

        return result;
    }

    @Override
    public List<Map<String, String>> extractDependencies(String content) {
        return new ArrayList<>();
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of();
    }

    @Override
    public String languageName() { return "Generic"; }

    private String detectLanguageFromPath(String filePath) {
        if (filePath == null) return "Unknown";
        String name = filePath.toLowerCase();
        if (name.endsWith(".ex") || name.endsWith(".exs")) return "Elixir";
        if (name.endsWith(".erl") || name.endsWith(".hrl")) return "Erlang";
        if (name.endsWith(".hs") || name.endsWith(".lhs")) return "Haskell";
        if (name.endsWith(".lua")) return "Lua";
        if (name.endsWith(".r") || name.endsWith(".R")) return "R";
        if (name.endsWith(".dart")) return "Dart";
        if (name.endsWith(".sol")) return "Solidity";
        if (name.endsWith(".jl")) return "Julia";
        if (name.endsWith(".m")) return "Objective-C";
        if (name.endsWith(".pl") || name.endsWith(".pm")) return "Perl";
        if (name.endsWith(".groovy") || name.endsWith(".gradle")) return "Groovy";
        if (name.endsWith(".vue")) return "Vue";
        if (name.endsWith(".svelte")) return "Svelte";
        return "Unknown";
    }
}
