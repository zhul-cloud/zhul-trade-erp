package com.zhul.erp.framework.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** /api/** 的跨域白名单来自 zhul.cors.allowed-origin-patterns：名单内的来源放行，名单外的拒绝（403） */
class WebMvcConfigCorsTest {

    @RestController
    static class DemoController {
        @PostMapping("/api/v1/demo")
        public Map<String, Object> demo() {
            return Map.of();
        }
    }

    /** 取出 CorsRegistry 生成的配置，交给 CorsFilter 按 Spring 的跨域规则处理 */
    static class ExposedCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configs() {
            return getCorsConfigurations();
        }
    }

    private MockMvc mvcWith(String patterns) {
        WebMvcConfig config = new WebMvcConfig();
        ReflectionTestUtils.setField(config, "allowedOriginPatterns", patterns);
        ExposedCorsRegistry registry = new ExposedCorsRegistry();
        config.addCorsMappings(registry);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.setCorsConfigurations(registry.configs());
        return MockMvcBuilders.standaloneSetup(new DemoController()).addFilters(new CorsFilter(source)).build();
    }

    private int postFrom(MockMvc mvc, String origin) throws Exception {
        return mvc.perform(post("/api/v1/demo").header(HttpHeaders.ORIGIN, origin))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void defaultAllowsOnlyLocalhost() throws Exception {
        MockMvc mvc = mvcWith("http://localhost:*");
        assertEquals(200, postFrom(mvc, "http://localhost:8000"));
        assertEquals(403, postFrom(mvc, "https://erp.zhul.com"));
        assertEquals(403, postFrom(mvc, "https://erp.zhulcloud.com"));
    }

    @Test
    void configuredOriginsAreTrimmedAndBlankItemsIgnored() throws Exception {
        MockMvc mvc = mvcWith(" https://erp.zhulcloud.com , ,https://*.example.com ");
        assertEquals(200, postFrom(mvc, "https://erp.zhulcloud.com"));
        assertEquals(200, postFrom(mvc, "https://a.example.com"));
        assertEquals(403, postFrom(mvc, "https://evil.com"));
        assertEquals(403, postFrom(mvc, "http://erp.zhulcloud.com"));
        assertEquals(403, postFrom(mvc, "http://localhost:8000"));
    }

    @Test
    void preflightFromAllowedOriginReturnsCorsHeaders() throws Exception {
        MockHttpServletResponse res = mvcWith("https://erp.zhulcloud.com").perform(options("/api/v1/demo")
                        .header(HttpHeaders.ORIGIN, "https://erp.zhulcloud.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andReturn().getResponse();
        assertEquals(200, res.getStatus());
        assertEquals("https://erp.zhulcloud.com", res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void requestWithoutOriginIsNotCorsChecked() throws Exception {
        MockMvc mvc = mvcWith("https://erp.zhulcloud.com");
        assertEquals(200, mvc.perform(post("/api/v1/demo")).andReturn().getResponse().getStatus());
    }
}
