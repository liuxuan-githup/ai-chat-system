package com.lx.ai.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_calculation_rule")
public class sysCalculationRule {
    private String id;
    private String name;
    private String calculationLogic;
    private String formula;
    private String version;
    private String status;
    private LocalDateTime createTime;
}
