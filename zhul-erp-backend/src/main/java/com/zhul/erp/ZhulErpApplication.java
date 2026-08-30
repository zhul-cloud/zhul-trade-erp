package com.zhul.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZhulErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhulErpApplication.class, args);
    }
}
