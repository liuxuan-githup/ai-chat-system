package com.lx.ai.tools;

import com.lx.ai.entity.po.CustomerServiceFeedback;
import com.lx.ai.service.IcustomerServiceFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Component
public class FeedbackTools {

    // 使用@tool注解实现查询数据
    private final IcustomerServiceFeedbackService service;

    @Tool(description = "新增反馈，返回反馈单号")
    public Integer addFeedback(
            @ToolParam(description = "反馈人姓名/账号") String feedbackUser,
            @ToolParam(description = "反馈人联系方式") String contactInfo,
            @ToolParam(description = "核心问题") String coreProblem,
            @ToolParam(description = "具体问题场景描述") String problemScenario,
            @ToolParam(description = "补充说明",required = false) String otherSuggestion
    ){
        CustomerServiceFeedback customerServiceFeedback = new CustomerServiceFeedback();
        customerServiceFeedback.setFeedbackUser(feedbackUser);
        customerServiceFeedback.setFeedbackTime(LocalDateTime.now());
        customerServiceFeedback.setContactInfo(contactInfo);
        customerServiceFeedback.setCoreProblem(coreProblem);
        customerServiceFeedback.setProblemScenario(problemScenario);
        customerServiceFeedback.setOtherSuggestion(otherSuggestion);
        if (service.save(customerServiceFeedback)){
            return customerServiceFeedback.getId().intValue();
        }
        return null;
    }
}
