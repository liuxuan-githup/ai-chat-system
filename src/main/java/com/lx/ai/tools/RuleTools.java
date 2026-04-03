package com.lx.ai.tools;

import com.lx.ai.entity.po.sysCalculationRule;
import com.lx.ai.service.IsysCalculationRuleService;
import com.lx.ai.utils.OutputReviewer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;


@RequiredArgsConstructor
@Component
public class RuleTools {

    // 使用@tool注解实现查询数据
    private final IsysCalculationRuleService calculationRuleService;
    // 输出审查
    private final OutputReviewer outputReviewer;

    @Cacheable(value = "railwayKnowledge", key = "#question")
    @Tool(description = "根据条件查询计算方式")
    public String  getCalculationRule(@ToolParam(description = "查询的条件") String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        sysCalculationRule rule = calculationRuleService.query()
                .like("name", name)
                .eq("status", 0)
                .last("LIMIT 1")
                .one();

        String answer = "规则名称：" + rule.getName() + "\n计算方式：" + rule.getCalculationLogic();
        OutputReviewer.ReviewResult result = outputReviewer.review(answer);
        if (!result.isPass()) {
            return "输出内容违规，已拦截：" + result.getMsg();
        }
        return result.getSafeContent();
    }

}
