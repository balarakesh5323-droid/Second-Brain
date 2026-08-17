package com.secondbrain.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YamlParser implements LanguageParser {

    private static final Pattern KEY = Pattern.compile(
        "^\\s*(?<key>[a-zA-Z_][a-zA-Z0-9_-]*)\\s*:",
        Pattern.MULTILINE
    );

    private static final Pattern ANCHOR = Pattern.compile(
        "^\\s*(?<name>\\S+)\\s*&\\s*(?<alias>[a-zA-Z_][a-zA-Z0-9_]*)",
        Pattern.MULTILINE
    );

    @Override
    public Map<String, Object> parse(String filePath, String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("file", filePath);
        result.put("language", "YAML");

        List<String> keys = new ArrayList<>();
        Matcher keyMatcher = KEY.matcher(content);
        while (keyMatcher.find()) {
            keys.add(keyMatcher.group("key"));
        }

        List<String> anchors = new ArrayList<>();
        Matcher anchorMatcher = ANCHOR.matcher(content);
        while (anchorMatcher.find()) {
            anchors.add(anchorMatcher.group("alias"));
        }

        result.put("topLevelKeys", keys);
        result.put("anchors", anchors);
        result.put("classes", new ArrayList<>());
        result.put("functions", new ArrayList<>());
        result.put("imports", new ArrayList<>());

        return result;
    }

    @Override
    public List<Map<String, String>> extractDependencies(String content) {
        return new ArrayList<>();
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".yaml", ".yml");
    }

    @Override
    public String languageName() { return "YAML"; }
}
