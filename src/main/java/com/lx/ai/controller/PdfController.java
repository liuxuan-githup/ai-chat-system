package com.lx.ai.controller;

import com.lx.ai.entity.vo.Result;
import com.lx.ai.repository.ChatHistoryRepository;
import com.lx.ai.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor.FILTER_EXPRESSION;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/pdf")
public class PdfController {

    private final FileRepository fileRepository;

    private final VectorStore vectorStore;

    private final ChatClient pdfChatClient;

    private final ChatHistoryRepository chatHistoryRepository;

    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(String prompt, String chatId) {
        // 参数校验增强
        if (prompt == null || prompt.trim().isEmpty()) {
            return Flux.just("<div style='color:red;'>提问内容不能为空！</div>");
        }
        if (chatId == null || chatId.trim().isEmpty()) {
            return Flux.just("<div style='color:red;'>会话ID不能为空！</div>");
        }

        // 找到会话文件
        Resource file = fileRepository.getFile(chatId);
        if (!file.exists()) {
            log.warn("PDF会话文件不存在，chatId:{}", chatId);
            return Flux.just("<div style='color:red;'>会话文件不存在！</div>");
        }

        // 保存会话id
        chatHistoryRepository.save("pdf", chatId);
        String fileName = Objects.requireNonNull(file.getFilename());
        log.info("开始PDF对话，chatId:{}, 文件名:{}, 提问:{}", chatId, fileName, prompt);

        // 请求模型
        return pdfChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .advisors(a -> a.param(FILTER_EXPRESSION, "file_name == '" + fileName + "'"))
                .stream()
                .content()
                .doOnError(e -> log.error("PDF对话异常，chatId:{}", chatId, e))
                .onErrorReturn("<div style='color:red;'>对话异常，请重试！</div>");
    }

    /**
     * 文件上传
     */
    @RequestMapping("/upload/{chatId}")
    public Result uploadPdf(@PathVariable String chatId, @RequestParam("file") MultipartFile file) {
        // 前置校验增强
        if (chatId == null || chatId.trim().isEmpty()) {
            return Result.fail("会话ID不能为空！");
        }
        if (file == null || file.isEmpty()) {
            return Result.fail("上传文件不能为空！");
        }

        // 校验PDF格式
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        if (!(Objects.equals(contentType, "application/pdf") ||
                (originalFilename != null && originalFilename.endsWith(".pdf")))) {
            log.warn("非PDF文件上传，文件名:{}, ContentType:{}", originalFilename, contentType);
            return Result.fail("只能上传PDF文件！");
        }

        try {
            // 保存文件
            boolean saveSuccess = fileRepository.save(chatId, file.getResource());
            if (!saveSuccess) {
                log.error("PDF文件保存失败，chatId:{}", chatId);
                return Result.fail("保存文件失败！");
            }

            // 写入向量库
            this.writeToVectorStore(file.getResource(), originalFilename);

            log.info("PDF上传成功，chatId:{}, 文件名:{}", chatId, originalFilename);
            return Result.ok();
        } catch (Exception e) {
            log.error("PDF上传异常，chatId:{}", chatId, e);
            return Result.fail("上传文件失败：" + e.getMessage());
        }
    }

    /**
     * 文件下载
     */
    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable("chatId") String chatId) throws IOException {
        // 读取文件
        Resource resource = fileRepository.getFile(chatId);
        if (!resource.exists()) {
            log.warn("PDF下载文件不存在，chatId:{}", chatId);
            return ResponseEntity.notFound().build();
        }

        // 文件名编码，避免中文乱码
        String filename = URLEncoder.encode(Objects.requireNonNull(resource.getFilename()), StandardCharsets.UTF_8);
        log.info("PDF下载成功，chatId:{}, 文件名:{}", chatId, filename);

        // 返回文件
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    /**
     * 写入向量库
     */
    private void writeToVectorStore(Resource resource, String fileName) {
        try {
            // 配置PDF读取器（优化格式、分片）
            PdfDocumentReaderConfig readerConfig = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                            .withNumberOfBottomTextLinesToDelete(0)
                            .withNumberOfTopTextLinesToDelete(0)
                            .build())
                    .withPagesPerDocument(1) // 每页一个Document，便于精准检索
                    .build();

            // 读取PDF并添加元数据（关联文件名，用于对话过滤）
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, readerConfig);
            List<Document> documents = reader.read();
            documents.forEach(doc -> {
                // 添加文件名称、唯一ID等元数据，便于后续检索过滤
                doc.getMetadata().put("file_name", fileName);
                doc.getMetadata().put("chat_id", UUID.randomUUID().toString().replace("-", ""));
                doc.getMetadata().put("source", "pdf_upload");
            });

            // 写入向量库
            vectorStore.add(documents);
            log.info("PDF向量写入成功，文件名:{}, 文档数:{}", fileName, documents.size());

        } catch (Exception e) {
            log.error("PDF向量写入失败，文件名:{}", fileName, e);
            throw new RuntimeException("向量库写入失败：" + e.getMessage());
        }
    }
}