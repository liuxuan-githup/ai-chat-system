package com.lx.ai.config;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 初始化 Chroma 客户端（与 Spring AI 无缝兼容）
 */
@Configuration
public class ChromaConfig {

    @Value("${spring.ai.chroma.base-url}")
    private String chromaBaseUrl;

    @Value("${spring.ai.chroma.collection-name}")
    private String collectionName;

    @Bean
    public ChromaEmbeddingStore chromaEmbeddingStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl(chromaBaseUrl)          // Chroma 服务地址
                .collectionName(collectionName)  // 向量集合名
                // 维度说明：llama3 约 4096、OpenAI ada-002 是 1536、M3E 是 768
                .build();
    }
}