package com.lx.ai.service;

import com.lx.ai.utils.DocumentCleaner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索增强生成核心链路服务
 *
 * 完整链路：
 *   1. 文档清洗  DocumentCleaner（去页码/乱码/换行断词/空白噪声）
 *   2. Chunk切分  TokenTextSplitter（Token 窗口 + Overlap 重叠）
 *   3. Embedding  VectorStore.add（Milvus 向量化入库）
 *   4. 向量检索  similaritySearch + metadata/scalar filter（chat_id / file_name / page_number）
 *   5. Rerank精排  RerankService（语义相关性重排，相关度回写 metadata）
 *   6. 上下文组装  buildContext（带来源/页码/相关度的可读上下文）
 */
@Slf4j
@Service
public class PdfRagService {

    /** 最终返回给 LLM 的文档数 */
    @Value("${spring.ai.dashscope.rerank.top-k:3}")
    private int topK = 3;

    /** 粗召回倍率：向量召回 topK * multiplier 条，再交给 Rerank 精排 */
    @Value("${spring.ai.dashscope.rerank.recall-multiplier:5}")
    private int recallMultiplier = 5;

    /** 向量召回相似度阈值（COSINE）：阈值偏松，交由 Rerank 兜底精排 */
    @Value("${spring.ai.dashscope.rerank.similarity-threshold:0.35}")
    private double similarityThreshold = 0.35;

    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;
    private final RerankService rerankService;

    public PdfRagService(VectorStore vectorStore,
                         TokenTextSplitter tokenTextSplitter,
                         RerankService rerankService) {
        this.vectorStore = vectorStore;
        this.tokenTextSplitter = tokenTextSplitter;
        this.rerankService = rerankService;
    }

    /**
     * 文档入库：读取 → 清洗 → 分块 → 清理旧数据 → Embedding 入库
     *
     * @param chatId   会话 ID（向量隔离维度，写入 scalar 字段 chat_id）
     * @param resource PDF 资源
     */
    public void ingest(String chatId, Resource resource) {
        String fileName = resource.getFilename();

        // 1. 按页读取 PDF（每页一个 Document，自带 page_number metadata）
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource,
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                        .withPagesPerDocument(1)
                        .build()
        );
        List<Document> pageDocuments = reader.read();

        // 2. 文档清洗 + 补充 metadata + 过滤空白页
        List<Document> cleanedDocuments = new ArrayList<>();
        for (Document doc : pageDocuments) {
            String cleanedText = DocumentCleaner.clean(doc.getText());
            if (cleanedText.isBlank()) {
                // 纯图片页 / 纯页眉页脚页，清洗后为空，跳过入库
                continue;
            }
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("file_name", fileName);
            metadata.put("chat_id", chatId); // scalar filter 隔离维度
            cleanedDocuments.add(new Document(cleanedText, metadata));
        }

        // 3. Chunk 切分（Token 窗口 + Overlap 重叠，chunk 继承所属页的 metadata 含 page_number）
        List<Document> chunks = tokenTextSplitter.split(cleanedDocuments);
        log.info("PDF 解析完成：原始 {} 页 → 清洗后 {} 页 → 切分 {} 个 Chunk，file={}",
                pageDocuments.size(), cleanedDocuments.size(), chunks.size(), fileName);

        // 4. 清理该会话下该文件的旧向量（避免重复上传 / 换文件导致数据堆积）
        deleteOld(chatId, fileName);

        // 5. Embedding 入库
        vectorStore.add(chunks);
        log.info("文档入库完成：file={}, chunks={}", fileName, chunks.size());
    }

    /**
     * 检索增强：向量粗召回（metadata/scalar filter）→ Rerank 精排
     *
     * @param chatId   会话 ID（filter 维度）
     * @param fileName 文件名（filter 维度）
     * @param query    用户问题
     * @return 精排后的文档列表（含 relevance_score 等 metadata）
     */
    public List<Document> retrieve(String chatId, String fileName, String query) {
        // 1. 构建 metadata / scalar filter：chat_id == ? AND file_name == ?
        Filter.Expression filter = byChatAndFile(chatId, fileName);

        // 2. 向量粗召回（多召回，交给 Rerank 精排）
        int recallCount = topK * recallMultiplier;
        List<Document> recallDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(recallCount)
                        .similarityThreshold(similarityThreshold)
                        .filterExpression(filter)
                        .build()
        );
        log.info("向量粗召回 {} 条，query={}", recallDocuments.size(), query);

        // 3. Rerank 精排
        if (recallDocuments.size() <= topK) {
            return recallDocuments;
        }
        List<Document> reranked = rerankService.rerank(query, recallDocuments, topK);
        log.info("Rerank 精排：{} 条 → {} 条", recallDocuments.size(), reranked.size());
        return reranked;
    }

    /**
     * 上下文组装：带来源（文件名/页码/相关度）的可读文本
     */
    public String buildContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "（未找到相关参考资料）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            Map<String, Object> meta = doc.getMetadata();
            sb.append("【参考资料").append(i + 1).append("】");
            sb.append(" 来源：").append(meta.getOrDefault("file_name", "未知"));
            if (meta.get("page_number") != null) {
                sb.append(" 第").append(meta.get("page_number")).append("页");
            }
            if (meta.get("relevance_score") instanceof Number score) {
                sb.append(" 相关度：").append(String.format("%.2f", score.doubleValue()));
            }
            sb.append('\n').append(doc.getText()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 构造 chat_id + file_name 的过滤表达式（避免字符串拼接的注入/转义问题）
     */
    private Filter.Expression byChatAndFile(String chatId, String fileName) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.and(b.eq("chat_id", chatId), b.eq("file_name", fileName)).build();
    }

    /**
     * 删除某会话某文件的旧向量（重复上传时先清旧数据）
     */
    private void deleteOld(String chatId, String fileName) {
        try {
            vectorStore.delete(byChatAndFile(chatId, fileName));
            log.info("已清除旧向量：chatId={}, file={}", chatId, fileName);
        } catch (Exception e) {
            // 向量库偶发异常不应阻断上传入库
            log.warn("清除旧向量失败（继续入库）：{}", e.getMessage());
        }
    }

    /**
     * 按会话删除全部向量数据（删除PDF时联动清理）
     */
    public void deleteByChatId(String chatId) {
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            vectorStore.delete(b.eq("chat_id", chatId).build());
            log.info("已清除向量数据：chatId={}", chatId);
        } catch (Exception e) {
            log.warn("清除向量数据失败：{}", e.getMessage());
        }
    }
}
