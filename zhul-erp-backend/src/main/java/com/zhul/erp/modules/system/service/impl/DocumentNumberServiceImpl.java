package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.DocumentSequenceMapper;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import org.springframework.stereotype.Service;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DocumentNumberServiceImpl implements DocumentNumberService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern PREFIX = Pattern.compile("^[A-Z]{2,4}$");
    private static final int PLATFORM = 0;
    private static final int MAX_ATTEMPTS = 5;

    private final DocumentSequenceMapper sequenceMapper;
    private final SysConfigMapper sysConfigMapper;
    private final LogService logService;
    private final TransactionTemplate numberTx;

    public DocumentNumberServiceImpl(DocumentSequenceMapper sequenceMapper, SysConfigMapper sysConfigMapper, LogService logService,
                                     PlatformTransactionManager transactionManager) {
        this.sequenceMapper = sequenceMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.logService = logService;
        this.numberTx = new TransactionTemplate(transactionManager);
        this.numberTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.numberTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public String next(DocumentType type) {
        int tenant = tenantId();
        LocalDate today = LocalDate.now();
        for (int attempt = 1; ; attempt++) {
            try {
                // 独立事务：取号立即提交，调用方回滚只留下空号
                Integer seq = numberTx.execute(status -> {
                    sequenceMapper.increment(tenant, type.name(), today);
                    return sequenceMapper.current(tenant, type.name(), today);
                });
                return format(type.external() ? prefix() : "", type, today, seq == null ? 1 : seq);
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BizException("单据编号生成繁忙，请重试", e);
                }
            }
        }
    }

    static String format(String prefix, DocumentType type, LocalDate date, int seq) {
        return (prefix == null ? "" : prefix) + type.name() + date.format(DATE) + String.format("%03d", seq);
    }

    @Override
    public String prefix() {
        int tenant = tenantId();
        SysConfigDO own = tenant == PLATFORM ? null : configRow(tenant);
        SysConfigDO row = own != null ? own : configRow(PLATFORM);
        return row == null || row.getConfigValue() == null ? "" : row.getConfigValue().trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String savePrefix(String prefix) {
        String value = prefix == null ? "" : prefix.trim();
        if (!value.isEmpty() && !PREFIX.matcher(value).matches()) {
            throw new BizException("单据前缀只能是 2–4 位大写字母");
        }
        int tenant = tenantId();
        String before = prefix();
        SysConfigDO own = configRow(tenant);
        if (own != null) {
            own.setConfigValue(value);
            sysConfigMapper.updateById(own);
        } else {
            SysConfigDO platform = configRow(PLATFORM);
            SysConfigDO row = new SysConfigDO();
            row.setTenantId(tenant);
            row.setConfigKey(CONFIG_PREFIX);
            row.setConfigName(platform == null ? "单据编号前缀" : platform.getConfigName());
            row.setConfigValue(value);
            row.setConfigType("STRING");
            row.setIsBuiltin(1);
            row.setIsEncrypted(0);
            row.setConfigGroup("document");
            row.setRemark(platform == null ? "" : platform.getRemark());
            sysConfigMapper.insert(row);
        }
        logService.recordOperateLog("系统设置", "修改单据前缀", Map.of("prefix", before), Map.of("prefix", value));
        return value;
    }

    private SysConfigDO configRow(int tenant) {
        return sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, CONFIG_PREFIX)
                .eq(SysConfigDO::getTenantId, tenant)
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
