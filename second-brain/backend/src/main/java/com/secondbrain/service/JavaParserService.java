package com.secondbrain.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class JavaParserService {

    public static Map<String, Object> parseJavaFile(String filePath, String content) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(content);

            Map<String, Object> result = new HashMap<>();
            result.put("file", filePath);

            cu.getPackageDeclaration().ifPresent(pkg ->
                result.put("package", pkg.getNameAsString()));

            List<String> imports = cu.getImports().stream()
                .map(imp -> imp.getNameAsString())
                .toList();
            result.put("imports", imports);

            List<Map<String, Object>> classes = new ArrayList<>();
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("name", clazz.getNameAsString());
                classInfo.put("isInterface", clazz.isInterface());
                classInfo.put("isAbstract", clazz.isAbstract());

                List<String> extendsList = clazz.getExtendedTypes().stream()
                    .map(t -> t.getNameAsString()).toList();
                classInfo.put("extends", extendsList);

                List<String> implementsList = clazz.getImplementedTypes().stream()
                    .map(t -> t.getNameAsString()).toList();
                classInfo.put("implements", implementsList);

                List<Map<String, String>> fields = clazz.getFields().stream()
                    .flatMap(f -> f.getVariables().stream())
                    .map(v -> Map.of(
                        "name", v.getNameAsString(),
                        "type", v.getTypeAsString()
                    )).toList();
                classInfo.put("fields", fields);

                List<Map<String, Object>> methods = clazz.getMethods().stream()
                    .map(m -> {
                        Map<String, Object> methodInfo = new HashMap<>();
                        methodInfo.put("name", m.getNameAsString());
                        methodInfo.put("returnType", m.getTypeAsString());
                        methodInfo.put("isAbstract", m.isAbstract());
                        methodInfo.put("isStatic", m.isStatic());
                        List<String> params = m.getParameters().stream()
                            .map(p -> p.getTypeAsString() + " " + p.getNameAsString())
                            .toList();
                        methodInfo.put("parameters", params);
                        return methodInfo;
                    }).toList();
                classInfo.put("methods", methods);

                List<String> annotations = clazz.getAnnotations().stream()
                    .map(a -> a.getNameAsString()).toList();
                classInfo.put("annotations", annotations);

                classes.add(classInfo);
            });
            result.put("classes", classes);

            return result;
        } catch (Exception e) {
            log.debug("Failed to parse Java file {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    public static List<Map<String, String>> extractDependencies(String content) {
        List<Map<String, String>> deps = new ArrayList<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(content);
            cu.getImports().forEach(imp -> {
                String name = imp.getNameAsString();
                if (!name.startsWith("java.") && !name.startsWith("javax.")) {
                    deps.add(Map.of(
                        "name", name,
                        "type", "import"
                    ));
                }
            });
        } catch (Exception e) {
            log.debug("Failed to extract dependencies: {}", e.getMessage());
        }
        return deps;
    }
}
