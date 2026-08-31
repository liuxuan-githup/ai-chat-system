package com.lx.ai.repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPdfFileRepository implements FileRepository {

    private final VectorStore vectorStore;

    // 会话id 与 文件名的对应关系，方便查询会话历史时重新加载文件
    private final Properties chatFiles = new Properties();

    @Override
    public boolean save(String chatId, Resource resource) {
        // 1.保存到本地磁盘
        String filename = resource.getFilename();
        File target = new File(Objects.requireNonNull(filename));
        if (!target.exists()) {
            try {
                Files.copy(resource.getInputStream(), target.toPath());
            } catch (IOException e) {
                log.error("Failed to save PDF resource.", e);
                return false;
            }
        }
        // 2.保存映射关系
        chatFiles.put(chatId, filename);
        return true;
    }

    @Override
    public Resource getFile(String chatId) {
        return new FileSystemResource(chatFiles.getProperty(chatId));
    }

    @Override
    public boolean delete(String chatId) {
        String filename = chatFiles.getProperty(chatId);
        if (filename == null) {
            log.warn("会话映射不存在，无需删除：chatId={}", chatId);
            return false;
        }
        // 1.删除物理文件
        File file = new File(filename);
        if (file.exists() && !file.delete()) {
            log.error("删除物理文件失败：{}", filename);
            return false;
        }
        // 2.删除会话映射并立即持久化（防止重启后映射恢复）
        chatFiles.remove(chatId);
        try {
            chatFiles.store(new FileWriter("chat-pdf.properties"), LocalDateTime.now().toString());
        } catch (IOException e) {
            log.error("持久化会话映射失败", e);
        }
        log.info("已删除PDF：chatId={}, file={}", chatId, filename);
        return true;
    }

    @PostConstruct
    private void init() {
        FileSystemResource pdfResource = new FileSystemResource("chat-pdf.properties");
        if (pdfResource.exists()) {
            try {
                chatFiles.load(new BufferedReader(new InputStreamReader(pdfResource.getInputStream(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // Milvus向量库自动持久化，无需手动加载
        log.info("Milvus VectorStore 初始化完成");
    }

    @PreDestroy
    private void persistent() {
        try {
            chatFiles.store(new FileWriter("chat-pdf.properties"), LocalDateTime.now().toString());
            // Milvus向量库自动持久化，无需手动保存
            log.info("会话映射已保存，Milvus向量数据自动持久化");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}