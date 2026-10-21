package com.issuemanagement.common.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.api-key:}")
    private String qdrantApiKey;

    @Value("${spring.ai.vectorstore.qdrant.use-tls:false}")
    private boolean qdrantUseTls;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient.Builder grpcClientBuilder =
                QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, qdrantUseTls, false);

        if (qdrantApiKey != null && !qdrantApiKey.isBlank()) {
            grpcClientBuilder.withApiKey(qdrantApiKey);
        }

        return new QdrantClient(grpcClientBuilder.build());
    }
}
