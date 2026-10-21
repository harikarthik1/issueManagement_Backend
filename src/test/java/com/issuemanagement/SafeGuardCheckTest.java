package com.issuemanagement;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;

public class SafeGuardCheckTest {
    @Test
    void testSafeGuard() throws Exception {
        SafeGuardAdvisor advisor = new SafeGuardAdvisor(
                java.util.List.of("weather", "sports", "recipe", "hack", "bypass", "politics", "crypto", "joke"),
                "I am only authorized to assist with queries related to the Issue Management System.",
                0
        );
        org.springframework.ai.chat.client.ChatClientRequest request = org.springframework.ai.chat.client.ChatClientRequest.builder()
                .prompt(new org.springframework.ai.chat.prompt.Prompt("Tell me about the weather"))
                .build();
        org.springframework.ai.chat.client.advisor.api.CallAdvisorChain chain = org.mockito.Mockito.mock(org.springframework.ai.chat.client.advisor.api.CallAdvisorChain.class);
        org.springframework.ai.chat.client.ChatClientResponse response = advisor.adviseCall(request, chain);
        System.out.println("ADVISE RESPONSE: " + response.chatResponse().getResult().getOutput().getText());
    }
}
