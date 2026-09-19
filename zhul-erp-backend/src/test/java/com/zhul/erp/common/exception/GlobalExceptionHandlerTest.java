package com.zhul.erp.common.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.zhul.erp.common.result.ErrorData;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    // 与 application.yml 的 jackson.default-property-inclusion: non_null 保持一致
    private final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    void plainBizExceptionKeepsOldShapeWithoutData() throws Exception {
        Result<ErrorData> result = handler.handleBizException(new BizException("客户不存在"));

        assertEquals(500, result.getCode());
        assertEquals("客户不存在", result.getMessage());
        assertNull(result.getData());
        assertTrue(mapper.readTree(mapper.writeValueAsString(result)).path("data").isMissingNode());
    }

    @Test
    void resultCodeExceptionKeepsNumericCode() {
        Result<ErrorData> result = handler.handleBizException(new BizException(ResultCode.USER_NOT_FOUND));

        assertEquals(1001, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void errorCodeAndDetailGoIntoData() throws Exception {
        BizException e = BizException.of("PRODUCT_DUPLICATE", "该型号已存在", Map.of("existingId", 12, "deleted", false));

        Result<ErrorData> result = handler.handleBizException(e);

        assertEquals(500, result.getCode());
        assertEquals("该型号已存在", result.getMessage());
        JsonNode json = mapper.readTree(mapper.writeValueAsString(result));
        assertEquals(500, json.path("code").asInt());
        assertEquals("PRODUCT_DUPLICATE", json.path("data").path("errorCode").asText());
        assertEquals(12, json.path("data").path("detail").path("existingId").asInt());
        assertEquals(false, json.path("data").path("detail").path("deleted").asBoolean(true));
    }

    @Test
    void errorCodeWithoutDetailOmitsDetail() throws Exception {
        Result<ErrorData> result = handler.handleBizException(BizException.of("PRODUCT_MPN_INVALID", "型号无效"));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(result));
        assertEquals("PRODUCT_MPN_INVALID", json.path("data").path("errorCode").asText());
        assertTrue(json.path("data").path("detail").isMissingNode());
    }

    @Test
    void oversizeUploadMessageDoesNotHardcodeFiveMegabytes() {
        Result<Void> result = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(100L * 1024 * 1024));

        assertEquals(500, result.getCode());
        assertTrue(!result.getMessage().contains("5MB"));
    }
}
