package com.secondbrain.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaScriptParser extends BaseLanguageParser {

    private static final Pattern CLASS = Pattern.compile(
        "^\\s*(?:export\\s+)?(?:default\\s+)?class\\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)" +
        "(?:\\s+extends\\s+(?<parent>[A-Za-z_$][A-Za-z0-9_$]*))?" +
        "(?:\\s+implements\\s+(?<interfaces>[A-Za-z_$][A-Za-z0-9_$, ]*))?" +
        "\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "^\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\s*\\*?\\s*(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\\s*\\((?<params>[^)]*)\\)" +
        "(?:\\s*:\\s*(?<returnType>[^\\s{]+))?\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern ARROW_FUNCTION = Pattern.compile(
        "^\\s*(?:export\\s+)?(?:default\\s+)?(?:const|let|var)\\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(?:async\\s+)?\\((?<params>[^)]*)\\)\\s*(?::\\s*(?<returnType>[^=]+))?\\s*=>",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*(?:import\\s+(?:\\{\\s*(?<names>[^}]+)\\s*\\}|(?<default>[A-Za-z_$][A-Za-z0-9_$]*))\\s+from\\s+['\"](?<module>[^'\"]+)['\"]|" +
        "(?:import\\s+['\"](?<module2>[^'\"]+)['\"])|" +
        "(?:(?<require>[A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*require\\s*\\(\\s*['\"](?<module3>[^'\"]+)['\"]))",
        Pattern.MULTILINE
    );

    private static final Pattern CONSTANT = Pattern.compile(
        "^\\s*(?:export\\s+)?(?:const\\s+)(?<name>[A-Z][A-Z0-9_]+)\\s*=\\s*(?<value>.+)",
        Pattern.MULTILINE
    );

    private static final Pattern INTERFACE = Pattern.compile(
        "^\\s*(?:export\\s+)?interface\\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)",
        Pattern.MULTILINE
    );

    @Override
    protected Pattern classPattern() { return CLASS; }

    @Override
    protected Pattern functionPattern() { return FUNCTION; }

    @Override
    protected Pattern importPattern() { return IMPORT; }

    @Override
    protected Pattern constantPattern() { return CONSTANT; }

    @Override
    protected Pattern interfacePattern() { return INTERFACE; }

    private static final Pattern EXPRESS_ENDPOINT = Pattern.compile(
        "(?:app|router)\\.(?<method>get|post|put|delete|patch|use)\\(\\s*['\"](?<path>[^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    @Override
    public Map<String, Object> parse(String filePath, String content) {
        Map<String, Object> parsed = super.parse(filePath, content);

        List<Map<String, Object>> functions = new ArrayList<>(
            (List<Map<String, Object>>) parsed.getOrDefault("functions", new ArrayList<>())
        );

        Matcher arrowMatcher = ARROW_FUNCTION.matcher(content);
        while (arrowMatcher.find()) {
            Map<String, Object> funcInfo = new HashMap<>();
            funcInfo.put("name", arrowMatcher.group("name"));
            funcInfo.put("parameters", arrowMatcher.group("params") != null ? arrowMatcher.group("params").trim() : "");
            if (arrowMatcher.group("returnType") != null) {
                funcInfo.put("returnType", arrowMatcher.group("returnType").trim());
            }
            funcInfo.put("isArrow", true);
            functions.add(funcInfo);
        }

        List<Map<String, Object>> endpoints = new ArrayList<>();
        Matcher epMatcher = EXPRESS_ENDPOINT.matcher(content);
        while (epMatcher.find()) {
            Map<String, Object> ep = new HashMap<>();
            ep.put("method", epMatcher.group("method").toUpperCase());
            ep.put("path", epMatcher.group("path"));
            ep.put("file", filePath);
            endpoints.add(ep);
        }

        parsed.put("functions", functions);
        parsed.put("endpoints", endpoints);
        return parsed;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".js", ".jsx", ".mjs", ".cjs");
    }

    @Override
    public String languageName() { return "JavaScript"; }
}
