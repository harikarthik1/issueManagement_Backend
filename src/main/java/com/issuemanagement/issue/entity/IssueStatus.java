package com.issuemanagement.issue.entity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public enum IssueStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    private static final Map<IssueStatus, Set<IssueStatus>> VALID_TRANSITIONS = Map.of(
            OPEN, Set.of(IN_PROGRESS, CLOSED),
            IN_PROGRESS, Set.of(RESOLVED, OPEN),
            RESOLVED, Set.of(CLOSED, IN_PROGRESS),
            CLOSED, Set.of(OPEN)
    );

    public boolean canTransitionTo(IssueStatus targetStatus) {
        if (this == targetStatus) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(targetStatus);
    }
}
