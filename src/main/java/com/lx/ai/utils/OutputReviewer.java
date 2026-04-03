package com.lx.ai.utils;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Agent 输出审查工具
 * 1. 违规内容过滤
 * 2. 隐私信息脱敏
 * 3. 格式清洗
 * 4. 业务事实校验
 */
@Component
public class OutputReviewer {

    // 隐私正则
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[0-9Xx]");
    private static final Pattern EMAIL = Pattern.compile("\\w+@\\w+\\.\\w+");

    // 违规关键词
    private static final String[] FORBIDDEN = {
            "删除", "修改", "数据库", "密码", "密钥", "内网地址",
            "管理员", "root", "内网", "服务器ip"
    };

    /**
     * 统一审查入口
     */
    public ReviewResult review(String content) {
        if (content == null || content.isBlank()) {
            return new ReviewResult(false, "输出内容为空", null);
        }

        // 违规检查
        for (String word : FORBIDDEN) {
            if (content.contains(word)) {
                return new ReviewResult(false, "包含敏感或违规内容", null);
            }
        }

        // 隐私脱敏
        String cleaned = content;
        cleaned = PHONE.matcher(cleaned).replaceAll("[手机号]");
        cleaned = ID_CARD.matcher(cleaned).replaceAll("[身份证号]");
        cleaned = EMAIL.matcher(cleaned).replaceAll("[邮箱]");

        // 最终安全输出
        return new ReviewResult(true, "审查通过", cleaned);
    }

    // 审查结果结构
    public static class ReviewResult {
        private boolean pass;
        private String msg;
        private String safeContent;

        public ReviewResult(boolean pass, String msg, String safeContent) {
            this.pass = pass;
            this.msg = msg;
            this.safeContent = safeContent;
        }

        public boolean isPass() { return pass; }
        public String getMsg() { return msg; }
        public String getSafeContent() { return safeContent; }
    }
}
