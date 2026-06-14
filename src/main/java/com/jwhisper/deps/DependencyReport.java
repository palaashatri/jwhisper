package com.jwhisper.deps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DependencyReport {
    private final List<DependencyIssue> issues = new ArrayList<>();

    public void add(DependencyIssue issue) {
        issues.add(issue);
    }

    public List<DependencyIssue> issues() {
        return Collections.unmodifiableList(issues);
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public boolean canTranscribe() {
        return issues.stream().noneMatch(DependencyIssue::blocksTranscription);
    }

    public String firstUserMessage() {
        if (issues.isEmpty()) {
            return "Ready.";
        }
        DependencyIssue issue = issues.get(0);
        if (issue.hint() == null || issue.hint().isBlank()) {
            return issue.message();
        }
        return issue.message() + "\n\n" + issue.hint();
    }
}
