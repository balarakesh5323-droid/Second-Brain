package com.secondbrain.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlParser implements LanguageParser {

    private static final Pattern TAG = Pattern.compile(
        "<(?<name>[a-zA-Z][a-zA-Z0-9]*)" +
        "(?<attributes>(?:\\s+[a-zA-Z-]+(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]*))?)*\\s*)" +
        "(?<selfClosing>/)?>",
        Pattern.DOTALL
    );

    private static final Pattern ATTRIBUTE = Pattern.compile(
        "(?<name>[a-zA-Z-]+)(?:\\s*=\\s*(?:\"(?<quoted>[^\"]*)\"|'(?<single>[^']*)'|(?<unquoted>[^\\s>]*)))?"
    );

    private static final Pattern META = Pattern.compile(
        "<meta\\s+(?:[^>]*\\s+)?(?:name|property|http-equiv)\\s*=\\s*[\"']([^\"']+)[\"']\\s+(?:content)\\s*=\\s*[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TITLE = Pattern.compile(
        "<title>([^<]+)</title>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern LINK = Pattern.compile(
        "<link\\s+(?:[^>]*\\s+)?(?:rel)\\s*=\\s*[\"']([^\"']+)[\"']\\s+(?:href)\\s*=\\s*[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SCRIPT = Pattern.compile(
        "<script(?:\\s+[^>]*)?\\s+(?:src)\\s*=\\s*[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CUSTOM_ELEMENT = Pattern.compile(
        "<(?<name>[a-z]+-[a-z][a-z0-9-]*)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
        "<script(?:\\s+[^>]*)?>([\\s\\S]*?)</script>",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public Map<String, Object> parse(String filePath, String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("file", filePath);
        result.put("language", "HTML");

        List<Map<String, Object>> elements = new ArrayList<>();
        List<Map<String, Object>> meta = new ArrayList<>();
        List<String> scripts = new ArrayList<>();
        List<String> stylesheets = new ArrayList<>();

        Matcher tagMatcher = TAG.matcher(content);
        while (tagMatcher.find()) {
            String tagName = tagMatcher.group("name").toLowerCase();
            String attrs = tagMatcher.group("attributes");

            Map<String, Object> elem = new HashMap<>();
            elem.put("tag", tagName);

            if (attrs != null && !attrs.isBlank()) {
                Map<String, String> attributes = new HashMap<>();
                Matcher attrMatcher = ATTRIBUTE.matcher(attrs);
                while (attrMatcher.find()) {
                    String attrName = attrMatcher.group("name");
                    String attrValue = attrMatcher.group("quoted");
                    if (attrValue == null) attrValue = attrMatcher.group("single");
                    if (attrValue == null) attrValue = attrMatcher.group("unquoted");
                    if (attrName != null) {
                        attributes.put(attrName, attrValue != null ? attrValue : "");
                    }
                }
                elem.put("attributes", attributes);
            }

            if ("script".equals(tagName) && elem.containsKey("attributes")) {
                Map<String, String> attrs2 = (Map<String, String>) elem.get("attributes");
                if (attrs2.containsKey("src")) {
                    scripts.add(attrs2.get("src"));
                }
            }
            if ("link".equals(tagName) && elem.containsKey("attributes")) {
                Map<String, String> attrs2 = (Map<String, String>) elem.get("attributes");
                if ("stylesheet".equals(attrs2.get("rel")) && attrs2.containsKey("href")) {
                    stylesheets.add(attrs2.get("href"));
                }
            }

            elements.add(elem);
        }

        Matcher titleMatcher = TITLE.matcher(content);
        if (titleMatcher.find()) {
            result.put("title", titleMatcher.group(1).trim());
        }

        Matcher metaMatcher = META.matcher(content);
        while (metaMatcher.find()) {
            meta.add(Map.of("name", metaMatcher.group(1), "content", metaMatcher.group(2)));
        }

        List<Map<String, Object>> functions = new ArrayList<>();
        List<Map<String, Object>> classes = new ArrayList<>();
        JavaScriptParser jsParser = new JavaScriptParser();

        Matcher scriptBlockMatcher = SCRIPT_BLOCK.matcher(content);
        while (scriptBlockMatcher.find()) {
            String scriptCode = scriptBlockMatcher.group(1);
            if (scriptCode != null && !scriptCode.isBlank()) {
                Map<String, Object> jsParsed = jsParser.parse(filePath, scriptCode);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jsFuncs = (List<Map<String, Object>>) jsParsed.getOrDefault("functions", List.of());
                functions.addAll(jsFuncs);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jsClasses = (List<Map<String, Object>>) jsParsed.getOrDefault("classes", List.of());
                classes.addAll(jsClasses);
            }
        }

        result.put("elements", elements);
        result.put("meta", meta);
        result.put("scripts", scripts);
        result.put("stylesheets", stylesheets);
        result.put("classes", classes);
        result.put("functions", functions);
        result.put("imports", new ArrayList<>());

        return result;
    }

    @Override
    public List<Map<String, String>> extractDependencies(String content) {
        List<Map<String, String>> deps = new ArrayList<>();

        Matcher scriptMatcher = SCRIPT.matcher(content);
        while (scriptMatcher.find()) {
            deps.add(Map.of("name", scriptMatcher.group(1), "type", "script"));
        }

        Matcher linkMatcher = LINK.matcher(content);
        while (linkMatcher.find()) {
            if ("stylesheet".equals(linkMatcher.group(1))) {
                deps.add(Map.of("name", linkMatcher.group(2), "type", "stylesheet"));
            }
        }

        return deps;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".html", ".htm", ".xhtml", ".vue", ".svelte");
    }

    @Override
    public String languageName() { return "HTML"; }
}
