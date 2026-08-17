package com.secondbrain.parser;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LanguageParser {

    Map<String, Object> parse(String filePath, String content);

    List<Map<String, String>> extractDependencies(String content);

    Set<String> supportedExtensions();

    default String languageName() {
        return getClass().getSimpleName().replace("Parser", "");
    }
}
