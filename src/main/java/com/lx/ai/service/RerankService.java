package com.lx.ai.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文本重排序服务接口
 * 对向量检索召回的候选文档进行语义相关性精排
 */
public interface RerankService {

    /**
     * 对文档列表进行重排序
     * @param query 用户查询问题
     * @param documents 向量检索召回的候选文档
     * @param topN 返回前N个最相关的
     * @return 重排序后的文档列表（按相关性从高到低）
     */
    List<Document> rerank(String query, List<Document> documents, int topN);
}
