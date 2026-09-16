package com.example.springai.stateful;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot 入口，只负责启动。真正的装配在 config 包里。 */
@SpringBootApplication
public class StatefulAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatefulAgentApplication.class, args);
    }
}
