package com.secondbrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SecretRedactionService {

    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "(api[_-]?key|apikey|secret|password|token|auth)[\"\\s]*[=:][\"\\s]*['\"]([^'\"]+)['\"]",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JWT_PATTERN = Pattern.compile(
        "eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
    );

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
        "-----BEGIN (RSA |EC )?PRIVATE KEY-----[\\s\\S]+-----END (RSA |EC )?PRIVATE KEY-----"
    );

    public String redactSecrets(String content) {
        if (content == null) return null;

        String redacted = content;
        redacted = API_KEY_PATTERN.matcher(redacted).replaceAll("$1: [REDACTED]");
        redacted = JWT_PATTERN.matcher(redacted).replaceAll("[REDACTED_JWT]");
        redacted = PRIVATE_KEY_PATTERN.matcher(redacted).replaceAll("[REDACTED_PRIVATE_KEY]");

        if (!redacted.equals(content)) {
            log.warn("Secrets detected and redacted in content");
        }

        return redacted;
    }

    public boolean containsSecrets(String content) {
        if (content == null) return false;
        return API_KEY_PATTERN.matcher(content).find() ||
               JWT_PATTERN.matcher(content).find() ||
               PRIVATE_KEY_PATTERN.matcher(content).find();
    }
}
