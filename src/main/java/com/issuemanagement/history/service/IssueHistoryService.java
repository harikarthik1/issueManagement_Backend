package com.issuemanagement.history.service;

import com.issuemanagement.history.dto.IssueHistoryResponse;
import com.issuemanagement.history.entity.ChangeType;
import com.issuemanagement.history.entity.IssueHistory;
import com.issuemanagement.history.repository.IssueHistoryRepository;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.issue.entity.Issue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueHistoryService {

    private final IssueHistoryRepository issueHistoryRepository;

    @Transactional
    public void recordHistory(Issue issue, User actor, ChangeType changeType, String fieldName, String oldValue, String newValue) {
        IssueHistory history = IssueHistory.builder()
                .issue(issue)
                .actor(actor)
                .changeType(changeType)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        issueHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<IssueHistoryResponse> getHistoryForIssue(Long issueId) {
        return issueHistoryRepository.findByIssueIdWithActor(issueId)
                .stream()
                .map(IssueHistoryResponse::fromEntity)
                .toList();
    }
}
