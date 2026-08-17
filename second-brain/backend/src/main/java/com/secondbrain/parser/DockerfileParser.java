package com.secondbrain.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DockerfileParser implements LanguageParser {

    private static final Pattern INSTRUCTION = Pattern.compile(
        "^\\s*(?<keyword>[A-Z][A-Z_]+)\\s+(?<args>.+)",
        Pattern.MULTILINE
    );

    private static final Pattern FROM = Pattern.compile(
        "^\\s*FROM\\s+(?<image>[^\\s:]+)(?::(?<tag>[^\\s]+))?(?:\\s+AS\\s+(?<alias>[^\\s]+))?",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ENV = Pattern.compile(
        "^\\s*ENV\\s+(?<key>[A-Z_][A-Z0-9_]*)\\s*[= ](?<value>.+)",
        Pattern.MULTILINE
    );

    private static final Pattern ARG = Pattern.compile(
        "^\\s*ARG\\s+(?<name>[A-Z_][A-Z0-9_]*)\\s*(?:=(?<default>.+))?",
        Pattern.MULTILINE
    );

    @Override
    public Map<String, Object> parse(String filePath, String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("file", filePath);
        result.put("language", "Dockerfile");

        List<String> baseImages = new ArrayList<>();
        Matcher fromMatcher = FROM.matcher(content);
        while (fromMatcher.find()) {
            String image = fromMatcher.group("image");
            String tag = fromMatcher.group("tag");
            baseImages.add(tag != null ? image + ":" + tag : image);
        }

        List<Map<String, String>> envVars = new ArrayList<>();
        Matcher envMatcher = ENV.matcher(content);
        while (envMatcher.find()) {
            envVars.add(Map.of("key", envMatcher.group("key"), "value", envMatcher.group("value").trim()));
        }

        List<Map<String, String>> buildArgs = new ArrayList<>();
        Matcher argMatcher = ARG.matcher(content);
        while (argMatcher.find()) {
            Map<String, String> arg = new HashMap<>();
            arg.put("name", argMatcher.group("name"));
            if (argMatcher.group("default") != null) {
                arg.put("default", argMatcher.group("default").trim());
            }
            buildArgs.add(arg);
        }

        List<String> instructions = new ArrayList<>();
        Matcher instMatcher = INSTRUCTION.matcher(content);
        while (instMatcher.find()) {
            instructions.add(instMatcher.group("keyword"));
        }

        result.put("baseImages", baseImages);
        result.put("environmentVariables", envVars);
        result.put("buildArgs", buildArgs);
        result.put("instructions", instructions);
        result.put("classes", new ArrayList<>());
        result.put("functions", new ArrayList<>());
        result.put("imports", new ArrayList<>());

        return result;
    }

    @Override
    public List<Map<String, String>> extractDependencies(String content) {
        List<Map<String, String>> deps = new ArrayList<>();
        Matcher fromMatcher = FROM.matcher(content);
        while (fromMatcher.find()) {
            deps.add(Map.of("name", fromMatcher.group("image"), "type", "base_image"));
        }
        return deps;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("Dockerfile", ".dockerfile", "docker-compose.yml", "docker-compose.yaml");
    }

    @Override
    public String languageName() { return "Dockerfile"; }
}
