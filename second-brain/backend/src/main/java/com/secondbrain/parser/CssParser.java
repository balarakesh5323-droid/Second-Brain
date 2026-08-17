package com.secondbrain.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CssParser implements LanguageParser {

    private static final Pattern SELECTOR = Pattern.compile(
        "^\\s*(?<selector>[.#@]?[a-zA-Z_][a-zA-Z0-9_ :>+~.\\-()\\[\\]=\"'\\*]*)\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern MEDIA_QUERY = Pattern.compile(
        "^\\s*@media\\s+(?<query>[^{]+)\\s*\\{",
        Pattern.MULTILINE
    );

    private static final Pattern KEYFRAME = Pattern.compile(
        "^\\s*@keyframes\\s+(?<name>[a-zA-Z_][a-zA-Z0-9_-]*)",
        Pattern.MULTILINE
    );

    private static final Pattern CUSTOM_PROPERTY = Pattern.compile(
        "^\\s*(?<name>--[a-zA-Z_][a-zA-Z0-9_-]*)\\s*:",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*@import\\s+(?:url\\()?['\"]?(?<module>[^'\"()\\s]+)['\"]?",
        Pattern.MULTILINE
    );

    @Override
    public Map<String, Object> parse(String filePath, String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("file", filePath);
        result.put("language", "CSS");

        List<String> selectors = new ArrayList<>();
        Matcher selectorMatcher = SELECTOR.matcher(content);
        while (selectorMatcher.find()) {
            selectors.add(selectorMatcher.group("selector").trim());
        }

        List<String> mediaQueries = new ArrayList<>();
        Matcher mediaMatcher = MEDIA_QUERY.matcher(content);
        while (mediaMatcher.find()) {
            mediaQueries.add(mediaMatcher.group("query").trim());
        }

        List<String> keyframes = new ArrayList<>();
        Matcher keyframeMatcher = KEYFRAME.matcher(content);
        while (keyframeMatcher.find()) {
            keyframes.add(keyframeMatcher.group("name"));
        }

        List<String> customProps = new ArrayList<>();
        Matcher propMatcher = CUSTOM_PROPERTY.matcher(content);
        while (propMatcher.find()) {
            customProps.add(propMatcher.group("name"));
        }

        result.put("selectors", selectors);
        result.put("mediaQueries", mediaQueries);
        result.put("keyframes", keyframes);
        result.put("customProperties", customProps);
        result.put("classes", new ArrayList<>());
        result.put("functions", new ArrayList<>());
        result.put("imports", new ArrayList<>());

        return result;
    }

    @Override
    public List<Map<String, String>> extractDependencies(String content) {
        List<Map<String, String>> deps = new ArrayList<>();
        Matcher matcher = IMPORT.matcher(content);
        while (matcher.find()) {
            deps.add(Map.of("name", matcher.group("module"), "type", "stylesheet"));
        }
        return deps;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".css", ".scss", ".sass", ".less", ".styl");
    }

    @Override
    public String languageName() { return "CSS"; }
}
