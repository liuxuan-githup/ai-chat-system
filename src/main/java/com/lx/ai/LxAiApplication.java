package com.lx.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@MapperScan("com.lx.ai.mapper")
@SpringBootApplication
@EnableCaching // 开启缓存功能
public class LxAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LxAiApplication.class, args);
    }

}
