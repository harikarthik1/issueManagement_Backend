package com.issuemanagement.assistant.cache;

public record SemanticCacheEligibilityResult(
        boolean eligible,
        String bypassReason
) {
    public static SemanticCacheEligibilityResult ofEligible() {
        return new SemanticCacheEligibilityResult(true, null);
    }

    public static SemanticCacheEligibilityResult ofBypass(String reason) {
        return new SemanticCacheEligibilityResult(false, reason);
    }
}
