package com.lx.ai.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("customer_service_feedback")
public class CustomerServiceFeedback {
    // id
    private Long id;
    // 反馈人姓名/账号
    private String feedbackUser;
    // 反馈时间
    private LocalDateTime feedbackTime;
    // 反馈人联系方式
    private String contactInfo;
    // 核心问题
    private String coreProblem;
    // 具体问题场景描述
    private String problemScenario;
    // 其他补充说明
    private String otherSuggestion;
}
