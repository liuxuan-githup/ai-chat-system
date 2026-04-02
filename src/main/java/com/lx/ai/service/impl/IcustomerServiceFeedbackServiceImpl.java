package com.lx.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.ai.entity.po.CustomerServiceFeedback;
import com.lx.ai.mapper.CustomerServiceFeedbackMapper;
import com.lx.ai.service.IcustomerServiceFeedbackService;
import org.springframework.stereotype.Service;

@Service
public class IcustomerServiceFeedbackServiceImpl extends ServiceImpl<CustomerServiceFeedbackMapper, CustomerServiceFeedback> implements IcustomerServiceFeedbackService {
}
