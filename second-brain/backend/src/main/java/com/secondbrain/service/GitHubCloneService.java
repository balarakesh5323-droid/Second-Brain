package com.secondbrain.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GitHubCloneService {

    @Value("${github.clone-base-dir:/data/repos}")
    private String cloneBaseDir;

    @Value("${github.token:}")
    private String githubToken;

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
        "(?:https?://)?(?:www\\.)?github\\.com/(?<owner>[A-Za-z0-9._-]+)/(?<repo>[A-Za-z0-9._-]+)(?:\\.git)?(?:/.*)?"
    );

    public static record CloneResult(
        String localPath,
        String owner,
        String repoName,
        String remoteUrl,
        String defaultBranch,
        boolean alreadyCloned
    ) {}

    public CloneResult cloneRepository(String url) throws GitAPIException, IOException {
        ParsedUrl parsed = parseGitHubUrl(url);
        String sanitizedRepo = parsed.repoName().replace(".git", "");
        String dirName = parsed.owner() + "-" + sanitizedRepo;
        Path targetDir = Path.of(cloneBaseDir, dirName);

        if (Files.exists(targetDir.resolve(".git"))) {
            log.info("Repository already cloned at {}, pulling latest", targetDir);
            pullLatest(targetDir);
            String branch = detectBranch(targetDir);
            return new CloneResult(
                targetDir.toString(),
                parsed.owner(),
                sanitizedRepo,
                url,
                branch,
                true
            );
        }

        Files.createDirectories(targetDir.getParent());

        log.info("Cloning {} to {}", url, targetDir);
        CloneCommand cloneCommand = Git.cloneRepository()
            .setURI(parsed.httpsUrl())
            .setDirectory(targetDir.toFile())
            .setCloneAllBranches(false);

        if (githubToken != null && !githubToken.isBlank()) {
            cloneCommand.setCredentialsProvider(
                new UsernamePasswordCredentialsProvider("token", githubToken)
            );
        }

        try (Git git = cloneCommand.call()) {
            String branch = git.getRepository().getBranch();
            log.info("Cloned {} on branch {}", url, branch);
            return new CloneResult(
                targetDir.toString(),
                parsed.owner(),
                sanitizedRepo,
                url,
                branch,
                false
            );
        }
    }

    public Map<String, Object> getRepoInfo(String localPath) throws IOException {
        Path gitDir = Path.of(localPath, ".git");
        if (!Files.exists(gitDir)) {
            return Map.of("error", "Not a git repository: " + localPath);
        }

        Path head = gitDir.resolve("HEAD");
        String branch = "unknown";
        if (Files.exists(head)) {
            String content = Files.readString(head).trim();
            if (content.startsWith("ref: refs/heads/")) {
                branch = content.substring("ref: refs/heads/".length());
            }
        }

        Path configPath = gitDir.resolve("config");
        String remoteUrl = "";
        if (Files.exists(configPath)) {
            String config = Files.readString(configPath);
            Matcher m = Pattern.compile("url = (.+)").matcher(config);
            if (m.find()) {
                remoteUrl = m.group(1).trim();
            }
        }

        return Map.of(
            "path", localPath,
            "branch", branch,
            "remoteUrl", remoteUrl
        );
    }

    private void pullLatest(Path repoDir) {
        try (Git git = Git.open(repoDir.toFile())) {
            git.pull().call();
            log.info("Pulled latest changes for {}", repoDir);
        } catch (Exception e) {
            log.warn("Pull failed for {}: {}", repoDir, e.getMessage());
        }
    }

    private String detectBranch(Path repoDir) {
        try (Git git = Git.open(repoDir.toFile())) {
            return git.getRepository().getBranch();
        } catch (IOException e) {
            return "main";
        }
    }

    public ParsedUrl parseGitHubUrl(String url) {
        Matcher matcher = GITHUB_URL_PATTERN.matcher(url.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a valid GitHub URL: " + url);
        }
        String owner = matcher.group("owner");
        String repoName = matcher.group("repo").replace(".git", "");
        String httpsUrl = "https://github.com/" + owner + "/" + repoName + ".git";
        return new ParsedUrl(owner, repoName, httpsUrl, url.trim());
    }

    public record ParsedUrl(String owner, String repoName, String httpsUrl, String originalUrl) {}

    public boolean isGitHubUrl(String url) {
        return url != null && GITHUB_URL_PATTERN.matcher(url.trim()).find();
    }
}
