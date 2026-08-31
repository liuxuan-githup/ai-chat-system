package com.lx.ai.controller;

import com.lx.ai.entity.vo.Result;
import com.lx.ai.repository.ChatHistoryRepository;
import com.lx.ai.utils.PromptGuardUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class GameController {

    private final ChatClient gameChatClient;

    private final ChatHistoryRepository chatHistoryRepository;

    private final ChatMemory chatMemory;

    @RequestMapping(value = "/game", produces = "text/html;charset=utf-8")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "chatId", required = false) String chatId) {
        // 0.参数校验：chatId 缺失返回 400，由前端自行提示，错误不推给终端用户
        if (!StringUtils.hasText(chatId)) {
            log.warn("游戏请求缺少 chatId，前端需以 query 参数传递，prompt={}", prompt);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 chatId 参数");
        }
        // 0.5 敏感词/无效提问拦截
        if (PromptGuardUtil.isIllegal(prompt)) {
            log.warn("游戏问答拦截违规提问：prompt={}", prompt);
            return Flux.just(PromptGuardUtil.getIllegalMsg());
        }
        // 1.保存会话id
        chatHistoryRepository.save("game", chatId);
        // 2.请求模型
        return gameChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }

    /**
     * 删除会话：清除聊天记录 + 从会话列表移除
     */
    @DeleteMapping("/game/{chatId}")
    public Result deleteGame(@PathVariable("chatId") String chatId) {
        try {
            chatMemory.clear(chatId);
            chatHistoryRepository.delete("game", chatId);
            log.info("删除会话完成：chatId={}", chatId);
            return Result.ok();
        } catch (Exception e) {
            log.error("Failed to delete game chat.", e);
            return Result.fail("删除会话失败！");
        }
    }
}
