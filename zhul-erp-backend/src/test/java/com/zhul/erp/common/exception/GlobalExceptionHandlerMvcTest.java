package com.zhul.erp.common.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 调用方的请求错误按真实 HTTP 状态返回，不能被兜底处理成 500「系统错误」 */
class GlobalExceptionHandlerMvcTest {

    @RestController
    static class DemoController {
        @PutMapping("/demo/{id}/cancel")
        public Map<String, Object> cancel() {
            return Map.of();
        }

        @GetMapping("/demo/list")
        public Map<String, Object> list(@RequestParam Integer page) {
            return Map.of("page", page);
        }

        @PutMapping("/demo/save")
        public Map<String, Object> save(@RequestBody Map<String, Object> body) {
            return body;
        }

        @GetMapping("/demo/boom")
        public Map<String, Object> boom() {
            throw new IllegalStateException("意外错误");
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DemoController()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private JsonNode call(RequestBuilder req, int expectedStatus) throws Exception {
        MockHttpServletResponse res = mvc.perform(req).andReturn().getResponse();
        assertEquals(expectedStatus, res.getStatus(), res.getContentAsString(StandardCharsets.UTF_8));
        JsonNode body = mapper.readTree(res.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(expectedStatus, body.path("code").asInt(), "code 与 HTTP 状态一致");
        return body;
    }

    @Test
    void wrongMethodIs405WithSupportedMethod() throws Exception {
        JsonNode body = call(post("/demo/11/cancel"), 405);
        assertEquals("接口不支持 POST 请求，请使用 PUT", body.path("message").asText());
    }

    @Test
    void missingParameterIs400() throws Exception {
        assertEquals("缺少参数：page", call(get("/demo/list"), 400).path("message").asText());
    }

    @Test
    void wrongParameterTypeIs400() throws Exception {
        assertEquals("参数格式不正确：page", call(get("/demo/list").param("page", "abc"), 400).path("message").asText());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        JsonNode body = call(put("/demo/save").contentType(MediaType.APPLICATION_JSON).content("{bad json"), 400);
        assertEquals("请求内容格式不正确", body.path("message").asText());
    }

    @Test
    void unsupportedContentTypeIs415() throws Exception {
        call(put("/demo/save").contentType(MediaType.TEXT_PLAIN).content("x"), 415);
    }

    @Test
    void unknownPathIs404() {
        ResponseEntity<?> res = new GlobalExceptionHandler().handleNoResource(new NoResourceFoundException(HttpMethod.GET, "api/v1/nope"));
        assertEquals(404, res.getStatusCode().value());
    }

    @Test
    void unexpectedExceptionStaysSystemError() throws Exception {
        assertEquals("系统错误", call(get("/demo/boom"), 500).path("message").asText());
    }
}
