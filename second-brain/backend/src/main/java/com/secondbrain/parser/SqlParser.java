package com.secondbrain.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlParser implements LanguageParser {

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE\\s+(?:OR\\s+REPLACE\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?<name>[`\"\\[]?[a-zA-Z_][a-zA-Z0-9_`\"\\]]+)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern CREATE_INDEX = Pattern.compile(
        "CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?<name>[`\"\\[]?[a-zA-Z_][a-zA-Z0-9_`\"\\]]+)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern CREATE_VIEW = Pattern.compile(
        "CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+(?<name>[`\"\\[]?[a-zA-Z_][a-zA-Z0-9_`\"\\]]+)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern FUNCTION = Pattern.compile(
        "CREATE\\s+(?:OR\\s+REPLACE\\s+)?FUNCTION\\s+(?<name>[`\"\\[]?[a-zA-Z_][a-zA-Z0-9_`\"\\]]+)\\s*\\((?<params>[^)]*)\\)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern PROCEDURE = Pattern.compile(
        "CREATE\\s+(?:OR\\s+REPLACE\\s+)?PROCEDURE\\s+(?<name>[`\"\\[]?[a-zA-Z_][a-zA-Z0-9_`\"\\]]+)\\s*\\((?<params>[^)]*)\\)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern INSERT_INTO = Pattern.compile(
        "INSERT\\s+INTO\\s+(?<name>[`\"\\[]?[a-zA-Z_][a-zA-Z0-9_`\"\\]]+)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern SELECT_FROM = Pattern.compile(
        "FROM\\s+(?<name>[`\"\\[]?[a-zA-Z_][a-zA-Z0-9_`\"\\]]+)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    @Override
    public Map<String, Object> parse(String filePath, String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("file", filePath);
        result.put("language", "SQL");

        List<Map<String, Object>> objects = new ArrayList<>();

        addMatches(content, CREATE_TABLE, "table", objects);
        addMatches(content, CREATE_INDEX, "index", objects);
        addMatches(content, CREATE_VIEW, "view", objects);
        addMatches(content, FUNCTION, "function", objects);
        addMatches(content, PROCEDURE, "procedure", objects);

        List<String> tables = new ArrayList<>();
        Matcher insertMatcher = INSERT_INTO.matcher(content);
        while (insertMatcher.find()) {
            String name = insertMatcher.group("name").replaceAll("[`\"\\[\\]]", "");
            if (!tables.contains(name)) tables.add(name);
        }

        Matcher selectMatcher = SELECT_FROM.matcher(content);
        while (selectMatcher.find()) {
            String name = selectMatcher.group("name").replaceAll("[`\"\\[\\]]", "");
            if (!tables.contains(name)) tables.add(name);
        }

        result.put("databaseObjects", objects);
        result.put("referencedTables", tables);
        result.put("classes", new ArrayList<>());
        result.put("functions", new ArrayList<>());
        result.put("imports", new ArrayList<>());

        return result;
    }

    private void addMatches(String content, Pattern pattern, String type, List<Map<String, Object>> objects) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> obj = new HashMap<>();
            obj.put("name", matcher.group("name").replaceAll("[`\"\\[\\]]", ""));
            obj.put("type", type);
            if (matcher.groupCount() > 1 && matcher.group("params") != null) {
                obj.put("parameters", matcher.group("params"));
            }
            objects.add(obj);
        }
    }

    @Override
    public List<Map<String, String>> extractDependencies(String content) {
        return new ArrayList<>();
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".sql", ".ddl", ".dml");
    }

    @Override
    public String languageName() { return "SQL"; }
}
