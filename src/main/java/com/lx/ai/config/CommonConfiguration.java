package com.lx.ai.config;

import com.lx.ai.constants.SystemConstants;
import com.lx.ai.entity.po.Msg;
import com.lx.ai.memory.RedisChatMemory;
import com.lx.ai.model.AlibabaOpenAiChatModel;
import com.lx.ai.tools.FeedbackTools;
import com.lx.ai.tools.RuleTools;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.autoconfigure.openai.OpenAiChatProperties;
import org.springframework.ai.autoconfigure.openai.OpenAiConnectionProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Configuration
public class CommonConfiguration {

   /* // 内存记忆将会话消息保存到jvm堆里面
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }*/

    @Bean
    public ChatMemory chatMemory(RedisTemplate<String, Msg> redisTemplate) {
        return new RedisChatMemory(redisTemplate);
    }

    // OpenAi的模型过期了
    /*@Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }*/

    // Milvus 自动配置按 EmbeddingModel 类型注入会有歧义，这里指定 Ollama(bge-m3) 为主，
    // 与 Milvus 配置的 embedding-dimension: 1024 保持一致。
    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(OllamaEmbeddingModel ollamaEmbeddingModel) {
        return ollamaEmbeddingModel;
    }

    // 智能文本切分器（Chunk + Overlap）
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(
                800,    // chunkSize：每个Chunk约800个token
                100,    // chunkOverlap：相邻Chunk重叠100个token，防止上下文断裂
                5,      // minChunkSizeChars：最小Chunk字符数
                10000,  // maxNumChunks：最大Chunk数量
                true    // keepSeparator：保留分隔符
        );
    }

    // 使用spring ai 的ChatMemory+Advisors实现多轮对话记忆
    @Bean
    public ChatClient chatClient(AlibabaOpenAiChatModel model, ChatMemory chatMemory, ToolCallbackProvider mcpTools, FeedbackTools feedbackTool) {
       /* OpenAiChatOptions options = OpenAiChatOptions
                .builder()
                .model("qwen3.5-ocr")
                .streamUsage(true)
                .temperature(0.7)
                .topP(0.95)
                .build();*/
        return ChatClient
                .builder(model)
                .defaultSystem("你是一家名为“liuxuan有限公司”的科技企业的智能助手，你的名字叫“小刘”。你要用专业、亲切且充满耐心的语气与用户交流。"
                        + "当用户表达对系统的反馈、不满、投诉或改进建议时（例如“回复慢”“报错”“不好用”“提个建议”等），"
                        + "请主动调用 addFeedback 工具新建反馈：尽量从对话中提取反馈人、联系方式、核心问题和场景描述，"
                        + "用户没有提供的信息留空即可，不要编造。")
                .defaultTools(mcpTools, feedbackTool)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .build();
    }

    @Bean
    public ChatClient gameChatClient(AlibabaOpenAiChatModel model, ChatMemory chatMemory,ToolCallbackProvider mcpTools) {
        return ChatClient
                .builder(model)
                .defaultSystem(SystemConstants.GAME_SYSTEM_PROMPT)
                .defaultAdvisors(
                        // 日志
                        new SimpleLoggerAdvisor(),
                        // 会话记忆
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .defaultTools(mcpTools)
                .build();
    }

    @Bean
    public ChatClient serviceChatClient(AlibabaOpenAiChatModel model
            , ChatMemory chatMemory
            , FeedbackTools feedbackTool
            , RuleTools ruleTools) {
        return ChatClient
                .builder(model)
                .defaultSystem(SystemConstants.SERVICE_SYSTEM_PROMPT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .defaultTools(feedbackTool,ruleTools)
                .build();
    }

    @Bean
    public ChatClient pdfChatClient(AlibabaOpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .build();
    }

    @Bean
    public AlibabaOpenAiChatModel alibabaOpenAiChatModel(OpenAiConnectionProperties commonProperties, OpenAiChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<WebClient.Builder> webClientBuilderProvider, ToolCallingManager toolCallingManager, RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler, ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<ChatModelObservationConvention> observationConvention) {
        String baseUrl = StringUtils.hasText(chatProperties.getBaseUrl()) ? chatProperties.getBaseUrl() : commonProperties.getBaseUrl();
        String apiKey = StringUtils.hasText(chatProperties.getApiKey()) ? chatProperties.getApiKey() : commonProperties.getApiKey();
        String projectId = StringUtils.hasText(chatProperties.getProjectId()) ? chatProperties.getProjectId() : commonProperties.getProjectId();
        String organizationId = StringUtils.hasText(chatProperties.getOrganizationId()) ? chatProperties.getOrganizationId() : commonProperties.getOrganizationId();
        Map<String, List<String>> connectionHeaders = new HashMap<>();
        if (StringUtils.hasText(projectId)) {
            connectionHeaders.put("OpenAI-Project", List.of(projectId));
        }

        if (StringUtils.hasText(organizationId)) {
            connectionHeaders.put("OpenAI-Organization", List.of(organizationId));
        }
        RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
        OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(baseUrl).apiKey(new SimpleApiKey(apiKey)).headers(CollectionUtils.toMultiValueMap(connectionHeaders)).completionsPath(chatProperties.getCompletionsPath()).embeddingsPath("/v1/embeddings").restClientBuilder(restClientBuilder).webClientBuilder(webClientBuilder).responseErrorHandler(responseErrorHandler).build();
        AlibabaOpenAiChatModel chatModel = AlibabaOpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(chatProperties.getOptions()).toolCallingManager(toolCallingManager).retryTemplate(retryTemplate).observationRegistry((ObservationRegistry) observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP)).build();
        Objects.requireNonNull(chatModel);
        observationConvention.ifAvailable(chatModel::setObservationConvention);
        return chatModel;
    }
}
