package com.zhul.erp.modules.masterdata.customerprofile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** 每天凌晨按租户重算客户的经营等级与档案完整度；一个租户失败不影响其他租户 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerNightlyJob {

    private final CustomerMapper customerMapper;
    private final CustomerGradeService gradeService;
    private final ProfileFacts profileFacts;

    @Scheduled(cron = "${zhul.customer.nightly-cron:0 30 2 * * *}", zone = "Asia/Shanghai")
    public void run() {
        for (Object t : customerMapper.selectObjs(new QueryWrapper<CustomerDO>().select("DISTINCT tenant_id").isNull("deleted_at"))) {
            int tenantId = ((Number) t).intValue();
            TenantContext.setTenantId(tenantId);
            try {
                recompute(tenantId);
            } catch (RuntimeException e) {
                log.error("客户经营等级与完整度重算失败，tenantId={}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** 重算一个租户；调用方负责设置 TenantContext */
    public void recompute(int tenantId) {
        List<CustomerDO> customers = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, tenantId)
                .isNull(CustomerDO::getDeletedAt));
        int grades = gradeService.recomputeTenant(tenantId, customers);
        int masks = profileFacts.refreshAll(tenantId, customers);
        log.info("客户经营等级与完整度重算完成，tenantId={}，客户 {} 个，等级变化 {}，完整度变化 {}", tenantId, customers.size(), grades, masks);
    }
}
