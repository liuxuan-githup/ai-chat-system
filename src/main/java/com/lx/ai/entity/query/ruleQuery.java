package com.lx.ai.entity.query;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

@Data
public class ruleQuery {
    @ToolParam(description = "规则名称")
    private String name;
}
