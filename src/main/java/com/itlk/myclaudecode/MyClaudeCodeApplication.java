package com.itlk.myclaudecode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MyClaudeCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyClaudeCodeApplication.class, args);
    }

}
