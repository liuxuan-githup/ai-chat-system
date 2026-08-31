package com.lx.ai.utils;

import java.util.regex.Pattern;

/**
 * PDF文档文本清洗工具
 * 在切分入库前，对PDF提取的原始文本做预处理，去除噪声、提升入库数据质量
 */
public class DocumentCleaner {

    // 连续3个及以上换行 → 压缩成2个（保留段落间隔）
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    // 行内连续空白（空格/制表符）→ 压缩成1个空格
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t\\x0B\\f]{2,}");

    // 英文单词被换行连字符断开：word-\nword → wordword
    private static final Pattern HYPHEN_LINE_BREAK = Pattern.compile("([A-Za-z])-\\s*\\n\\s*([A-Za-z])");

    // 中文之间的多余换行（中文本身不需要换行连接）：汉字\n汉字 → 汉字汉字
    private static final Pattern CJK_NEWLINE = Pattern.compile("([\\u4e00-\\u9fa5]) *\\n *([\\u4e00-\\u9fa5])");

    // 常见不可见/控制字符（保留常规换行\n和制表符\t）
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0E-\\x1F\\x7F]");

    // 页码类噪声：单独一行的纯数字，或"第X页"/"- X -"/"Page X"
    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "(?m)^\\s*(?:-\\s*\\d+\\s*-|第?\\s*\\d+\\s*页?|[Pp]age\\s*\\d+(?:\\s*of\\s*\\d+)?)\\s*$");

    // 常见PDF乱码替换字符
    private static final Pattern REPLACEMENT_CHAR = Pattern.compile("[\\uFFFD\\uFEFF]");

    /**
     * 清洗文本主入口
     */
    public static String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String result = text;

        // 1.去除控制字符和乱码替换字符
        result = CONTROL_CHARS.matcher(result).replaceAll("");
        result = REPLACEMENT_CHAR.matcher(result).replaceAll("");

        // 2.去除页码类噪声（先于换行处理，因为要匹配整行）
        result = PAGE_NUMBER.matcher(result).replaceAll("");

        // 3.修复英文换行连字符断词：inter-\nnational → international
        result = HYPHEN_LINE_BREAK.matcher(result).replaceAll("$1$2");

        // 4.合并被换行切断的中文句子
        result = CJK_NEWLINE.matcher(result).replaceAll("$1$2");

        // 5.压缩行内多余空白
        result = MULTI_SPACE.matcher(result).replaceAll(" ");

        // 6.压缩多余空行
        result = MULTI_NEWLINE.matcher(result).replaceAll("\n\n");

        // 7.去除每行首尾空白 + 整体首尾空白
        result = trimEachLine(result);

        return result.trim();
    }

    /**
     * 去除每一行首尾的空白字符
     */
    private static String trimEachLine(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i].strip());
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
