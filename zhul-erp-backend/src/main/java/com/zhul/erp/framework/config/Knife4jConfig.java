package com.zhul.erp.framework.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("烛龙ERP API")
                .description("外贸业财一体化智能中台接口文档")
                .version("v1.0.0")
                .contact(new Contact().name("烛龙团队")))
            .addSecurityItem(new SecurityRequirement().addList("Authorization"))
            .schemaRequirement("Authorization", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .name("Authorization"));
    }
}
