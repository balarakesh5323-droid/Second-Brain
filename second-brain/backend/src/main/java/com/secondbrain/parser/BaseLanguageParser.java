package com.secondbrain.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseLanguageParser implements LanguageParser {

    protected abstract Pattern classPattern();

    protected abstract Pattern functionPattern();

    protected abstract Pattern importPattern();

    protected Pattern interfacePattern() {
        return null;
    }

    protected Pattern structPattern() {
        return null;
    }

    protected Pattern enumPattern() {
        return null;
    }

    protected Pattern typeAliasPattern() {
        return null;
    }

    protected Pattern decoratorPattern() {
        return null;
    }

    protected Pattern constantPattern() {
        return null;
    }

    protected Pattern traitPattern() {
        return null;
    }

    protected Pattern implPattern() {
        return null;
    }

    protected Pattern protocolPattern() {
        return null;
    }

    protected Pattern modulePattern() {
        return null;
    }

    protected boolean isMultiLineComment(int line, int col, String content) {
        int lastBlockComment = content.lastIndexOf("/*", line > 0 ? line : 0);
        int closeBlockComment = content.lastIndexOf("*/", lastBlockComment);
        return closeBlockComment < lastBlockComment;
    }

    @Override
    public Map<String, Object> parse(String filePath, String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("file", filePath);
        result.put("language", languageName());

        List<Map<String, Object>> classes = new ArrayList<>();
        List<Map<String, Object>> functions = new ArrayList<>();
        List<Map<String, Object>> imports = new ArrayList<>();

        extractClasses(content, classes);
        extractInterfaces(content, classes);
        extractStructs(content, classes);
        extractEnums(content, classes);
        extractTraits(content, classes);
        extractProtocols(content, classes);
        extractModules(content, classes);
        extractTypeAliases(content, classes);
        extractFunctions(content, functions);
        extractConstants(content, functions);
        extractImports(content, imports);
        extractDecorators(content, classes);

        result.put("classes", classes);
        result.put("functions", functions);
        result.put("imports", imports);

        return result;
    }

    protected void extractClasses(String content, List<Map<String, Object>> classes) {
        Pattern pattern = classPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> classInfo = new HashMap<>();
            classInfo.put("name", matcher.group("name"));
            classInfo.put("type", "class");

            if (matcher.groupCount() > 1 && matcher.group("parent") != null) {
                classInfo.put("extends", List.of(matcher.group("parent")));
            }
            if (matcher.groupCount() > 2 && matcher.group("interfaces") != null) {
                classInfo.put("implements", List.of(matcher.group("interfaces").split(",")));
            }
            classes.add(classInfo);
        }
    }

    protected void extractInterfaces(String content, List<Map<String, Object>> classes) {
        Pattern pattern = interfacePattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", matcher.group("name"));
            info.put("type", "interface");
            classes.add(info);
        }
    }

    protected void extractStructs(String content, List<Map<String, Object>> classes) {
        Pattern pattern = structPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", matcher.group("name"));
            info.put("type", "struct");
            classes.add(info);
        }
    }

    protected void extractEnums(String content, List<Map<String, Object>> classes) {
        Pattern pattern = enumPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", matcher.group("name"));
            info.put("type", "enum");
            classes.add(info);
        }
    }

    protected void extractTraits(String content, List<Map<String, Object>> classes) {
        Pattern pattern = traitPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", matcher.group("name"));
            info.put("type", "trait");
            classes.add(info);
        }
    }

    protected void extractProtocols(String content, List<Map<String, Object>> classes) {
        Pattern pattern = protocolPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", matcher.group("name"));
            info.put("type", "protocol");
            classes.add(info);
        }
    }

    protected void extractModules(String content, List<Map<String, Object>> classes) {
        Pattern pattern = modulePattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", matcher.group("name"));
            info.put("type", "module");
            classes.add(info);
        }
    }

    protected void extractTypeAliases(String content, List<Map<String, Object>> classes) {
        Pattern pattern = typeAliasPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", matcher.group("name"));
            info.put("type", "type_alias");
            classes.add(info);
        }
    }

    protected void extractFunctions(String content, List<Map<String, Object>> functions) {
        Pattern pattern = functionPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> funcInfo = new HashMap<>();
            funcInfo.put("name", matcher.group("name"));
            if (matcher.groupCount() > 1 && matcher.group("params") != null) {
                funcInfo.put("parameters", matcher.group("params").trim());
            }
            if (matcher.groupCount() > 2 && matcher.group("returnType") != null) {
                funcInfo.put("returnType", matcher.group("returnType").trim());
            }
            functions.add(funcInfo);
        }
    }

    protected void extractConstants(String content, List<Map<String, Object>> functions) {
        Pattern pattern = constantPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", matcher.group("name"));
            info.put("type", "constant");
            if (matcher.groupCount() > 1 && matcher.group("value") != null) {
                info.put("value", matcher.group("value"));
            }
            functions.add(info);
        }
    }

    protected void extractImports(String content, List<Map<String, Object>> imports) {
        Pattern pattern = importPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> importInfo = new HashMap<>();
            importInfo.put("module", matcher.group("module"));
            if (matcher.groupCount() > 1 && matcher.group("names") != null) {
                importInfo.put("names", matcher.group("names"));
            }
            imports.add(importInfo);
        }
    }

    protected void extractDecorators(String content, List<Map<String, Object>> classes) {
        Pattern pattern = decoratorPattern();
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", matcher.group("name"));
            info.put("type", "decorator");
            classes.add(info);
        }
    }

    @Override
    public List<Map<String, String>> extractDependencies(String content) {
        List<Map<String, String>> deps = new ArrayList<>();
        Pattern pattern = importPattern();
        if (pattern == null) return deps;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String module = matcher.group("module");
            if (module != null && !module.isEmpty()) {
                deps.add(Map.of(
                    "name", module,
                    "type", "import"
                ));
            }
        }
        return deps;
    }
}
