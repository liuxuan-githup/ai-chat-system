package com.lx.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.lx.ai.mapper")
@SpringBootApplication
public class LxAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LxAiApplication.class, args);
    }

}
