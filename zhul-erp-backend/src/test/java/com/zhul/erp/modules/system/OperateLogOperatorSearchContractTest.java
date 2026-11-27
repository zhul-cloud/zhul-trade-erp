package com.zhul.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 操作日志按操作人搜索（BUS-100）：日志里记的是登录账号，列表显示姓名，
 * 所以按姓名、按账号都要能搜到。
 */
class OperateLogOperatorSearchContractTest extends TenantContractSupport {

    private static final int MENU_OPERATE_LOG = 100018;
    private static final String LOGS = "/api/v1/system/logs/operate";
    private static final String ME = "it_log_finder";
    private static final String OTHER = "it_log_other";

    @AfterEach
    void cleanLogs() {
        jdbc.update("delete from sys_log where operator_name in (?, ?)", ME, OTHER);
    }

    private String setUp() {
        loginWithResources(ME, MENU_OPERATE_LOG);
        jdbc.update("update user_basic set name = '日志查找员' where username = ?", ME);
        for (String who : List.of(ME, OTHER)) {
            jdbc.update("insert into sys_log (tenant_id, type, operator_name, operation, menu, operate_time)"
                    + " values (?, 2, ?, '编辑', '客户管理', now())", testUserTenant, who);
        }
        return token(ME, testUserTenant);
    }

    private List<String> operators(String keyword, String token) throws Exception {
        JsonNode res = ok(call(get(LOGS).param("operatorName", keyword).param("page", "1")
                .param("pageSize", "50"), token));
        List<String> out = new ArrayList<>();
        res.path("records").forEach(r -> out.add(r.path("operatorName").asText()));
        return out;
    }

    @Test
    void searchByName_findsLogsRecordedUnderAccount() throws Exception {
        String token = setUp();
        assertThat(operators("查找员", token)).containsOnly(ME).isNotEmpty();
    }

    @Test
    void searchByAccount_stillWorks() throws Exception {
        String token = setUp();
        assertThat(operators("it_log_oth", token)).containsOnly(OTHER).isNotEmpty();
    }
}
