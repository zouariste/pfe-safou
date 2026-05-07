package com.cisternino;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.*;

@SpringBootApplication
@ConfigurationPropertiesScan("com.cisternino")
public class CodeOwlApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeOwlApplication.class, args);
    }
}