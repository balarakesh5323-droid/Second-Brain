package com.secondbrain.parser;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LanguageParserFactory {

    private final Map<String, LanguageParser> extensionToParser = new LinkedHashMap<>();
    private final List<LanguageParser> parsers = new ArrayList<>();
    private final GenericParser genericParser = new GenericParser();

    @PostConstruct
    public void init() {
        registerParser(new JavaParserService());
        registerParser(new PythonParser());
        registerParser(new JavaScriptParser());
        registerParser(new TypeScriptParser());
        registerParser(new GoParser());
        registerParser(new CCppParser());
        registerParser(new RustParser());
        registerParser(new HtmlParser());
        registerParser(new RubyParser());
        registerParser(new PhpParser());
        registerParser(new KotlinParser());
        registerParser(new SwiftParser());
        registerParser(new ScalaParser());
        registerParser(new ShellParser());
        registerParser(new CSharpParser());
        registerParser(new CssParser());
        registerParser(new SqlParser());
        registerParser(new YamlParser());
        registerParser(new DockerfileParser());
    }

    private void registerParser(LanguageParser parser) {
        parsers.add(parser);
        for (String ext : parser.supportedExtensions()) {
            extensionToParser.put(ext.toLowerCase(), parser);
        }
    }

    public LanguageParser getParser(String fileExtension) {
        if (fileExtension == null) return genericParser;
        LanguageParser parser = extensionToParser.get(fileExtension.toLowerCase());
        return parser != null ? parser : genericParser;
    }

    public LanguageParser getParserForFile(String filePath) {
        if (filePath == null) return genericParser;
        String fileName = Path.of(filePath).getFileName().toString().toLowerCase();
        if (fileName.equals("dockerfile") || fileName.startsWith("dockerfile.")) {
            return extensionToParser.getOrDefault("dockerfile", genericParser);
        }
        if (fileName.equals("docker-compose.yml") || fileName.equals("docker-compose.yaml")) {
            return extensionToParser.getOrDefault("docker-compose.yml", genericParser);
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) return genericParser;
        String ext = fileName.substring(lastDot);
        return getParser(ext);
    }

    public Map<String, Object> parseFile(String filePath, String content) {
        LanguageParser parser = getParserForFile(filePath);
        return parser.parse(filePath, content);
    }

    public List<Map<String, String>> extractDependencies(String filePath, String content) {
        LanguageParser parser = getParserForFile(filePath);
        return parser.extractDependencies(content);
    }

    public Set<String> getSupportedExtensions() {
        return extensionToParser.keySet();
    }

    public Map<String, String> getExtensionToLanguageMap() {
        return extensionToParser.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().languageName(),
                (a, b) -> a,
                LinkedHashMap::new
            ));
    }

    public Collection<LanguageParser> getAllParsers() {
        return parsers;
    }
}
