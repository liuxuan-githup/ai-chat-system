package com.lx.ai.config;

import com.lx.ai.entity.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理：统一返回 {ok,msg} 结构，避免异常堆栈直接暴露给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 显式抛出的 HTTP 状态异常（如 400 缺参、404 会话不存在）：透传状态码与提示
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result> handleResponseStatus(ResponseStatusException e) {
        String reason = e.getReason();
        return ResponseEntity.status(e.getStatusCode())
                .body(Result.fail(reason != null ? reason : "请求失败"));
    }

    /**
     * 缺少 @RequestParam 必填参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(Result.fail("缺少必要参数：" + e.getParameterName()));
    }

    /**
     * 参数类型不匹配（如把非数字传给 Long）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(Result.fail("参数类型错误：" + e.getName()));
    }

    /**
     * 兜底：未预期的异常统一包装，避免堆栈泄露
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail("服务器内部错误，请稍后重试"));
    }
}
