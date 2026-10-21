package com.issuemanagement.issue.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueStatusTransitionTest {

    @Test
    @DisplayName("OPEN can transition to IN_PROGRESS and CLOSED, but not directly to RESOLVED")
    void testOpenTransitions() {
        assertTrue(IssueStatus.OPEN.canTransitionTo(IssueStatus.IN_PROGRESS));
        assertTrue(IssueStatus.OPEN.canTransitionTo(IssueStatus.CLOSED));
        assertFalse(IssueStatus.OPEN.canTransitionTo(IssueStatus.RESOLVED));
        assertTrue(IssueStatus.OPEN.canTransitionTo(IssueStatus.OPEN));
    }

    @Test
    @DisplayName("IN_PROGRESS can transition to RESOLVED and OPEN, but not directly to CLOSED")
    void testInProgressTransitions() {
        assertTrue(IssueStatus.IN_PROGRESS.canTransitionTo(IssueStatus.RESOLVED));
        assertTrue(IssueStatus.IN_PROGRESS.canTransitionTo(IssueStatus.OPEN));
        assertFalse(IssueStatus.IN_PROGRESS.canTransitionTo(IssueStatus.CLOSED));
        assertTrue(IssueStatus.IN_PROGRESS.canTransitionTo(IssueStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("RESOLVED can transition to CLOSED and IN_PROGRESS (reopened), but not directly to OPEN")
    void testResolvedTransitions() {
        assertTrue(IssueStatus.RESOLVED.canTransitionTo(IssueStatus.CLOSED));
        assertTrue(IssueStatus.RESOLVED.canTransitionTo(IssueStatus.IN_PROGRESS));
        assertFalse(IssueStatus.RESOLVED.canTransitionTo(IssueStatus.OPEN));
        assertTrue(IssueStatus.RESOLVED.canTransitionTo(IssueStatus.RESOLVED));
    }

    @Test
    @DisplayName("CLOSED can transition only to OPEN (reopened)")
    void testClosedTransitions() {
        assertTrue(IssueStatus.CLOSED.canTransitionTo(IssueStatus.OPEN));
        assertFalse(IssueStatus.CLOSED.canTransitionTo(IssueStatus.IN_PROGRESS));
        assertFalse(IssueStatus.CLOSED.canTransitionTo(IssueStatus.RESOLVED));
        assertTrue(IssueStatus.CLOSED.canTransitionTo(IssueStatus.CLOSED));
    }
}
