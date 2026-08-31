package com.lx.ai.controller;

import com.lx.ai.entity.vo.Result;
import com.lx.ai.repository.ChatHistoryRepository;
import com.lx.ai.repository.FileRepository;
import com.lx.ai.service.PdfRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/pdf")
public class PdfController {

    private final FileRepository fileRepository;

    private final ChatClient pdfChatClient;

    private final ChatHistoryRepository chatHistoryRepository;

    /** RAG 链路服务：清洗 → 切分 → Embedding → 向量检索 → Rerank → 上下文组装 */
    private final PdfRagService ragService;

    private final ChatMemory chatMemory;

    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "chatId", required = false) String chatId) {
        // 0.参数校验：chatId 缺失返回 400，由前端自行提示，错误不推给终端用户
        if (!StringUtils.hasText(chatId)) {
            log.warn("PDF问答请求缺少 chatId，前端需以 query 参数传递，prompt={}", prompt);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 chatId 参数");
        }
        // 1.找到会话文件
        Resource file = fileRepository.getFile(chatId);
        if (!file.exists()) {
            throw new RuntimeException("会话文件不存在！");
        }
        // 2.保存会话id
        chatHistoryRepository.save("pdf", chatId);

        // 3.RAG 检索增强：向量粗召回（metadata/scalar filter）→ Rerank 精排
        String fileName = file.getFilename();
        List<Document> documents = ragService.retrieve(chatId, fileName, prompt);
        log.info("RAG 最终上下文：{} 条", documents.size());

        // 4.组装上下文 + 构造带上下文的 System Prompt
        String context = ragService.buildContext(documents);
        String systemPrompt = buildSystemPrompt(context);

        // 5.调用LLM流式输出
        return pdfChatClient.prompt()
                .system(systemPrompt)
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }

    /**
     * 构造System Prompt
     */
    private String buildSystemPrompt(String context) {
        return "你是一个专业的文档问答助手，请根据下面的参考资料回答用户的问题。\n\n"
                + "==================== 参考资料 ====================\n"
                + context
                + "==================================================\n\n"
                + "回答规则：\n"
                + "1. 只基于上面的参考资料回答问题\n"
                + "2. 参考资料中没有的内容，请如实说明，不要编造\n"
                + "3. 回答要简洁、准确、有条理\n"
                + "4. 若引用了参考资料，请注明参考来源（文件名/页码）";
    }

    /**
     * 文件上传
     */
    @RequestMapping("/upload/{chatId}")
    public Result uploadPdf(@PathVariable String chatId, @RequestParam("file") MultipartFile file) {
        try {
            // 1. 校验文件是否为PDF格式
            if (!Objects.equals(file.getContentType(), "application/pdf")) {
                return Result.fail("只能上传PDF文件！");
            }
            // 2.保存文件
            boolean success = fileRepository.save(chatId, file.getResource());
            if (!success) {
                return Result.fail("保存文件失败！");
            }
            // 3.RAG 入库：文档清洗 → Chunk切分 → 清理旧数据 → Embedding
            ragService.ingest(chatId, file.getResource());
            return Result.ok();
        } catch (Exception e) {
            log.error("Failed to upload PDF.", e);
            return Result.fail("上传文件失败！");
        }
    }

    /**
     * 删除PDF：删除物理文件 + 会话映射 + Milvus向量 + 会话聊天记录/会话列表
     */
    @DeleteMapping("/{chatId}")
    public Result deletePdf(@PathVariable("chatId") String chatId) {
        try {
            // 删除 Milvus 向量数据
            ragService.deleteByChatId(chatId);
            // 删除物理文件及会话映射
            boolean deleted = fileRepository.delete(chatId);
            if (!deleted) {
                return Result.fail("文件不存在或删除失败！");
            }
            // 删除会话聊天记录（问答界面的历史消息）
            chatMemory.clear(chatId);
            // 从会话ID列表移除（问答界面不再显示该会话）
            chatHistoryRepository.delete("pdf", chatId);
            log.info("删除PDF完成：chatId={}", chatId);
            return Result.ok();
        } catch (Exception e) {
            log.error("Failed to delete PDF.", e);
            return Result.fail("删除文件失败！");
        }
    }

    /**
     * 文件下载
     */
    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable("chatId") String chatId) {
        // 1.读取文件
        Resource resource = fileRepository.getFile(chatId);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        // 2.文件名编码，写入响应头
        String filename = URLEncoder.encode(Objects.requireNonNull(resource.getFilename()), StandardCharsets.UTF_8);
        // 3.返回文件
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
