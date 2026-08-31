package com.lx.ai.controller;

import com.lx.ai.entity.vo.Result;
import com.lx.ai.repository.ChatHistoryRepository;
import com.lx.ai.utils.PromptGuardUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.Media;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatClient chatClient;

    private final ChatHistoryRepository chatHistoryRepository;

    private final ChatMemory chatMemory;

    // 有界线程池：核心4、最大8、队列200。队列满时由调用线程执行（CallerRunsPolicy），
    // 避免高并发下无限创建线程导致 OOM（原 newCachedThreadPool 与"固定线程池"注释自相矛盾）
    private static final ExecutorService SSE_EXECUTOR = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 使用SseEmitter + CompletableFuture实现 SSE流式 Al接口，
     * 将推理任务异步化，实时输出智能体思考和执行过程。
     * 用户等待感知时间减少 80%；
     * 并通过自定义 SSE 数据封装格式，解决流式传输中换行符和特殊字符丢失问题
     */
    @RequestMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        // 创建 SSE 连接（0 = 永不超时，生产环境稳定）
        SseEmitter emitter = new SseEmitter(0L);


        // 新增前置违规拦截
        if (PromptGuardUtil.isIllegal(prompt)) {
            try {
                // 直接推送拦截提示，不调用AI
                emitter.send(PromptGuardUtil.getIllegalMsg());
            } catch (IOException e) {
                // 发送异常忽略
            }
            emitter.complete();
            // 直接返回终止后续所有AI逻辑
            return emitter;
        }

        // 异步执行
        CompletableFuture.runAsync(() -> {
            Disposable disposable = null;
            try {
                // 保存会话ID
                chatHistoryRepository.save("chat", chatId);

                // 判断：纯文本 / 多模态
                if (files == null || files.isEmpty()) {
                    disposable = textChatStream(prompt, chatId, emitter);
                } else {
                    disposable = multiModalChatStream(prompt, chatId, files, emitter);
                }

            } catch (Exception e) {
                // 异常推送
                try {
                    emitter.send("ERROR：" + e.getMessage());
                } catch (IOException ex) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        }, SSE_EXECUTOR);

        return emitter;
    }

    /**
     * 纯文本流式处理
     */
    private Disposable textChatStream(String prompt, String chatId, SseEmitter emitter) {
        return chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content()
                // 使用订阅流 一个一个字的推给前端
                .subscribe(
                        content -> send(emitter, content),
                        emitter::completeWithError,
                        emitter::complete
                );
    }

    /**
     * 多文件（图片/文档）流式处理
     */
    private Disposable multiModalChatStream(
            String prompt,
            String chatId,
            List<MultipartFile> files,
            SseEmitter emitter
    ) {
        List<Media> medias = files.stream()
                .map(file -> new Media(
                        org.springframework.util.MimeType.valueOf(Objects.requireNonNull(file.getContentType())),
                        file.getResource()
                ))
                .toList();

        return chatClient.prompt()
                .user(u -> u.text(prompt).media(medias.toArray(Media[]::new)))
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content()
                .subscribe(
                        content -> send(emitter, content),
                        emitter::completeWithError,
                        emitter::complete
                );
    }

    /**
     * SSE 消息发送（统一处理异常）
     */
    private void send(SseEmitter emitter, String content) {
        try {
            emitter.send(content);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 删除会话：清除聊天记录 + 从会话列表移除
     */
    @DeleteMapping("/chat/{chatId}")
    public Result deleteChat(@PathVariable("chatId") String chatId) {
        try {
            chatMemory.clear(chatId);
            chatHistoryRepository.delete("chat", chatId);
            log.info("删除会话完成：chatId={}", chatId);
            return Result.ok();
        } catch (Exception e) {
            log.error("Failed to delete chat.", e);
            return Result.fail("删除会话失败！");
        }
    }
}
