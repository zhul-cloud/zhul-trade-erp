package com.zhul.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 登录走 JWT，不用 Spring Security 的内存账号；排除后启动不再生成并打印默认密码
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class ZhulErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhulErpApplication.class, args);
    }
}
