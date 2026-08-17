package com.secondbrain.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class JavaParserService implements LanguageParser {

    @Override
    public Map<String, Object> parse(String filePath, String content) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(content);

            Map<String, Object> result = new HashMap<>();
            result.put("file", filePath);
            result.put("language", "Java");

            cu.getPackageDeclaration().ifPresent(pkg ->
                result.put("package", pkg.getNameAsString()));

            List<Map<String, String>> imports = cu.getImports().stream()
                .map(imp -> Map.of("module", imp.getNameAsString()))
                .toList();
            result.put("imports", imports);

            List<Map<String, Object>> endpoints = new ArrayList<>();
            List<Map<String, Object>> functions = new ArrayList<>();
            List<Map<String, Object>> classes = new ArrayList<>();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("name", clazz.getNameAsString());
                classInfo.put("type", clazz.isInterface() ? "interface" : "class");
                classInfo.put("isAbstract", clazz.isAbstract());

                // Class-level RequestMapping
                String classBasePath = "";
                for (var ann : clazz.getAnnotations()) {
                    if (ann.getNameAsString().equals("RequestMapping")) {
                        classBasePath = ann.toString().replaceAll(".*\\(\"?(.*?)\"?\\).*", "$1");
                        if (classBasePath.equals(ann.toString())) classBasePath = "";
                    }
                }
                final String finalClassBasePath = classBasePath;

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
                        String methodName = m.getNameAsString();
                        methodInfo.put("name", methodName);
                        methodInfo.put("returnType", m.getTypeAsString());
                        methodInfo.put("isAbstract", m.isAbstract());
                        methodInfo.put("isStatic", m.isStatic());
                        m.getBegin().ifPresent(b -> methodInfo.put("startLine", b.line));
                        m.getEnd().ifPresent(e -> methodInfo.put("endLine", e.line));
                        m.getJavadocComment().ifPresent(c -> methodInfo.put("docstring", c.getContent().trim()));

                        List<String> params = m.getParameters().stream()
                            .map(p -> p.getTypeAsString() + " " + p.getNameAsString())
                            .toList();
                        methodInfo.put("parameters", params);

                        // Extract method call graph (invocations inside method body)
                        List<String> calledMethods = new ArrayList<>();
                        try {
                            m.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).forEach(call -> {
                                calledMethods.add(call.getNameAsString());
                            });
                        } catch (Exception ignored) {}
                        methodInfo.put("calls", calledMethods.stream().distinct().toList());

                        // Extract HTTP Endpoints from Spring annotations
                        for (var ann : m.getAnnotations()) {
                            String annName = ann.getNameAsString();
                            String httpMethod = switch (annName) {
                                case "GetMapping" -> "GET";
                                case "PostMapping" -> "POST";
                                case "PutMapping" -> "PUT";
                                case "DeleteMapping" -> "DELETE";
                                case "PatchMapping" -> "PATCH";
                                case "RequestMapping" -> "REQUEST";
                                default -> null;
                            };
                            if (httpMethod != null) {
                                String path = ann.toString().replaceAll(".*\\(\"?(.*?)\"?\\).*", "$1");
                                if (path.equals(ann.toString())) path = "";
                                String fullPath = (finalClassBasePath + (path.startsWith("/") ? path : (path.isEmpty() ? "" : "/" + path))).replaceAll("//+", "/");
                                if (fullPath.isEmpty()) fullPath = "/";

                                Map<String, Object> ep = new HashMap<>();
                                ep.put("method", httpMethod);
                                ep.put("path", fullPath);
                                ep.put("handlerFunction", methodName);
                                ep.put("className", clazz.getNameAsString());
                                ep.put("file", filePath);
                                endpoints.add(ep);
                            }
                        }

                        functions.add(methodInfo);
                        return methodInfo;
                    }).toList();
                classInfo.put("methods", methods);

                List<String> annotations = clazz.getAnnotations().stream()
                    .map(a -> a.getNameAsString()).toList();
                classInfo.put("annotations", annotations);

                classes.add(classInfo);
            });
            result.put("classes", classes);
            result.put("functions", functions);
            result.put("endpoints", endpoints);

            return result;
        } catch (Exception e) {
            log.debug("Failed to parse Java file {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    @Override
    public List<Map<String, String>> extractDependencies(String content) {
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

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".java");
    }

    @Override
    public String languageName() { return "Java"; }

    public static Map<String, Object> parseJavaFile(String filePath, String content) {
        return new JavaParserService().parse(filePath, content);
    }

    public static List<Map<String, String>> extractJavaDependencies(String content) {
        return new JavaParserService().extractDependencies(content);
    }
}
