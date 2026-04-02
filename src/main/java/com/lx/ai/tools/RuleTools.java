package com.lx.ai.tools;

import com.lx.ai.entity.po.sysCalculationRule;
import com.lx.ai.service.IsysCalculationRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;


@RequiredArgsConstructor
@Component
public class RuleTools {

    private final IsysCalculationRuleService calculationRuleService;

    @Tool(description = "根据条件查询计算方式")
    public sysCalculationRule getCalculationRule(@ToolParam(description = "查询的条件") String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return calculationRuleService.query()
                .like("name", name)
                .eq("status", 0)
                .last("LIMIT 1")
                .one();
    }

}
