package com.lx.ai.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ClassPathResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 提问防护：违规词、无效提问、Prompt注入拦截
 */
public class PromptGuardUtil {

    // 存放所有敏感词
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>();

    static {
        // 加载四份词库文件（classpath 资源，IDE运行与jar打包均可读取）
        loadWords("sensitive/abusive_words.txt");
        loadWords("sensitive/pornography.txt");
        loadWords("sensitive/gamble.txt");
        loadWords("sensitive/prompt_attack.txt");
    }

    /**
     * 从classpath读取txt里每行一个敏感词，放入集合
     */
    private static void loadWords(String classPath) {
        // 用 InputStream 从 classpath 读取，避免 FileUtil 读文件系统路径在 jar 打包后失效
        List<String> lines = IoUtil.readUtf8Lines(
                new ClassPathResource(classPath).getStream(), new ArrayList<>());
        for (String line : lines) {
            String word = line.trim();
            if (!word.isEmpty()) {
                SENSITIVE_WORDS.add(word.toLowerCase());
            }
        }
    }

    /**
     * true=违规要拦截；false=正常放行
     */
    public static boolean isIllegal(String prompt) {
        String text = prompt.trim();
        // 空内容直接拦截
        if (text.isBlank()) {
            return true;
        }
        String lowerText = text.toLowerCase();
        // 遍历匹配敏感词
        for (String word : SENSITIVE_WORDS) {
            if (lowerText.contains(word)) {
                return true;
            }
        }
        // 过滤灌水重复字符
        long distinctCharCount = text.chars().distinct().count();
        double repeatRatio = 1 - (double) distinctCharCount / text.length();
        return repeatRatio > 0.9 && text.length() > 4;
    }

    // 拦截提示文案
    public static String getIllegalMsg() {
        return "您的提问包含违规或者无意义内容，无法为您解答，请更换合规问题。";
    }
}