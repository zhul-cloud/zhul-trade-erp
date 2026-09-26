package com.zhul.erp.modules.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.SysLogDO;
import com.zhul.erp.modules.system.repository.AccountAccessTokenMapper;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.SysLogMapper;
import com.zhul.erp.modules.system.service.impl.LogServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogServiceOperateLogTest {

    @Mock private SysLogMapper sysLogMapper;
    @Mock private AccountMapper accountMapper;
    @Mock private AccountAccessTokenMapper accountAccessTokenMapper;
    @Mock private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void recordOperateLog_writesOperatorMenuOperationAndBeforeAfter() throws Exception {
        LogServiceImpl service = new LogServiceImpl(sysLogMapper, accountMapper, accountAccessTokenMapper,
                objectMapper, redisTemplate);
        TenantContext.setTenantId(7);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("wangqiang", null, List.of()));
        AccountDO operator = new AccountDO();
        operator.setId(501);
        operator.setUsername("wangqiang");
        when(accountMapper.selectOne(any())).thenReturn(operator);

        service.recordOperateLog("客户管理", "转移客户", Map.of("ownerName", "张伟"), Map.of("ownerName", "赵敏"));

        ArgumentCaptor<SysLogDO> captor = ArgumentCaptor.forClass(SysLogDO.class);
        verify(sysLogMapper).insert(captor.capture());
        SysLogDO log = captor.getValue();
        assertThat(log.getTenantId()).isEqualTo(7);
        assertThat(log.getMenu()).isEqualTo("客户管理");
        assertThat(log.getOperation()).isEqualTo("转移客户");
        assertThat(log.getOperatorCode()).isEqualTo("501");
        assertThat(log.getOperatorName()).isEqualTo("wangqiang");
        assertThat(log.getIp()).isEmpty();
        JsonNode content = objectMapper.readTree(log.getContent());
        assertThat(content.path("before").path("ownerName").asText()).isEqualTo("张伟");
        assertThat(content.path("after").path("ownerName").asText()).isEqualTo("赵敏");
    }
}
