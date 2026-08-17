package com.secondbrain.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GitService {

    public Repository openRepository(String path) throws IOException {
        return new FileRepositoryBuilder()
            .setGitDir(new File(path))
            .readEnvironment()
            .findGitDir()
            .build();
    }

    public List<Map<String, Object>> getRecentCommits(String repoPath, int count) throws Exception {
        List<Map<String, Object>> commits = new ArrayList<>();
        try (Repository repository = openRepository(repoPath)) {
            try (RevWalk walk = new RevWalk(repository)) {
                ObjectId head = repository.resolve("HEAD");
                if (head == null) return commits;

                walk.markStart(walk.parseCommit(head));
                int i = 0;
                for (RevCommit commit : walk) {
                    if (i >= count) break;
                    commits.add(Map.of(
                        "id", commit.getId().getName(),
                        "shortId", commit.getId().abbreviate(7).name(),
                        "message", commit.getFullMessage().trim(),
                        "author", commit.getAuthorIdent().getName(),
                        "email", commit.getAuthorIdent().getEmailAddress(),
                        "timestamp", commit.getAuthorIdent().getWhen().toString(),
                        "parentCount", commit.getParentCount()
                    ));
                    i++;
                }
            }
        }
        return commits;
    }

    public Map<String, Object> getCommitDetails(String repoPath, String commitId) throws Exception {
        try (Repository repository = openRepository(repoPath)) {
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
                Map<String, Object> details = new HashMap<>();
                details.put("id", commit.getId().getName());
                details.put("message", commit.getFullMessage().trim());
                details.put("author", commit.getAuthorIdent().getName());
                details.put("timestamp", commit.getAuthorIdent().getWhen().toString());

                List<String> files = new ArrayList<>();
                if (commit.getParentCount() > 0) {
                    RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
                    DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                    diffFormatter.setRepository(repository);
                    List<DiffEntry> diffs = diffFormatter.scan(parent, commit);
                    for (DiffEntry diff : diffs) {
                        files.add(diff.getNewPath());
                    }
                }
                details.put("files", files);
                return details;
            }
        }
    }

    public List<String> getBranches(String repoPath) throws Exception {
        List<String> branches = new ArrayList<>();
        try (Repository repository = openRepository(repoPath);
             Git git = new Git(repository)) {
            var branchList = git.branchList().call();
            for (var branch : branchList) {
                branches.add(branch.getName());
            }
        }
        return branches;
    }

    public String getCurrentBranch(String repoPath) throws Exception {
        try (Repository repository = openRepository(repoPath)) {
            return repository.getBranch();
        }
    }

    public List<String> getFileHistory(String repoPath, String filePath, int count) throws Exception {
        List<String> history = new ArrayList<>();
        try (Repository repository = openRepository(repoPath)) {
            LogCommand log = new Git(repository).log();
            log.addPath(filePath);
            int i = 0;
            for (RevCommit commit : log.call()) {
                if (i >= count) break;
                history.add(commit.getId().getName() + " - " + commit.getFullMessage().trim());
                i++;
            }
        }
        return history;
    }

    public List<String> getChangedFiles(String repoPath, String commitId) throws Exception {
        List<String> files = new ArrayList<>();
        try (Repository repository = openRepository(repoPath)) {
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
                if (commit.getParentCount() > 0) {
                    RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
                    DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                    diffFormatter.setRepository(repository);
                    List<DiffEntry> diffs = diffFormatter.scan(parent, commit);
                    for (DiffEntry diff : diffs) {
                        files.add(diff.getNewPath());
                    }
                }
            }
        }
        return files;
    }
}
