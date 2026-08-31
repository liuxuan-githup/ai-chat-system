package com.lx.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lx.ai.service.RerankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.autoconfigure.openai.OpenAiChatProperties;
import org.springframework.ai.autoconfigure.openai.OpenAiConnectionProperties;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 阿里云百炼 MaaS 工作空间文本重排序实现
 * 模型：qwen3-rerank（文本重排模型）
 * 端点：compatible-api/v1/reranks —— 注意是 compatible-api，不是 compatible-mode
 */
@Slf4j
@Service
public class DashScopeRerankServiceImpl implements RerankService {

    private final RestClient restClient;

    /** Rerank 服务地址（可配置，默认百炼 MaaS 工作空间 compatible-api 端点） */
    @Value("${spring.ai.dashscope.rerank.url:https://ws-c9xuhe72n1oof204.cn-beijing.maas.aliyuncs.com/compatible-api/v1/reranks}")
    private String rerankUrl;

    /** Rerank 模型（可配置，默认 qwen3-rerank） */
    @Value("${spring.ai.dashscope.rerank.model:qwen3-rerank}")
    private String rerankModel;

    public DashScopeRerankServiceImpl(OpenAiConnectionProperties commonProperties,
                                      OpenAiChatProperties chatProperties,
                                      ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        // 与 alibabaOpenAiChatModel 相同的方式获取 apiKey
        String apiKey = StringUtils.hasText(chatProperties.getApiKey())
                ? chatProperties.getApiKey()
                : commonProperties.getApiKey();
        // 与 alibabaOpenAiChatModel 相同的方式获取 RestClient.Builder
        RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        this.restClient = restClientBuilder
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public List<Document> rerank(String query, List<Document> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        if (documents.size() <= topN) {
            return documents;
        }

        try {
            // 1.构造请求体（OpenAI 兼容格式：query / documents / top_n 均为顶层字段）
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", rerankModel);
            requestBody.put("query", query);
            List<String> texts = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.toList());
            requestBody.put("documents", texts);
            requestBody.put("top_n", topN);

            // 2.调用API（RestClient风格，与项目一致）
            String responseBody = restClient.post()
                    .uri(rerankUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toJSONString())
                    .retrieve()
                    .body(String.class);

            // 3.解析响应：results[].index + results[].relevance_score
            JSONObject responseJson = JSON.parseObject(responseBody);
            JSONArray results = responseJson.getJSONArray("results");

            // 4.按重排后的索引重新排序文档，并把相关度写回 metadata（供上下文组装展示）
            List<Document> rerankedDocuments = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JSONObject result = results.getJSONObject(i);
                int index = result.getIntValue("index");
                if (index >= 0 && index < documents.size()) {
                    Document doc = documents.get(index);
                    if (result.containsKey("relevance_score")) {
                        doc.getMetadata().put("relevance_score", result.getDoubleValue("relevance_score"));
                    }
                    rerankedDocuments.add(doc);
                }
            }

            log.info("Rerank完成：召回{}条 → 精排{}条", documents.size(), rerankedDocuments.size());
            return rerankedDocuments;

        } catch (Exception e) {
            log.error("Rerank调用失败，降级返回原始检索结果", e);
            // 降级：返回前topN个（按向量相似度）
            return documents.subList(0, Math.min(topN, documents.size()));
        }
    }
}
