package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.system.dto.ExchangeRateLogVO;
import com.zhul.erp.modules.system.dto.ExchangeRateVO;
import com.zhul.erp.modules.system.entity.ExchangeRateDO;
import com.zhul.erp.modules.system.entity.ExchangeRateLogDO;
import com.zhul.erp.modules.system.repository.ExchangeRateLogMapper;
import com.zhul.erp.modules.system.repository.ExchangeRateMapper;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private static final int SCALE = 6;
    private static final BigDecimal MAX_RATE = new BigDecimal("1000000");
    private static final int SOURCE_MANUAL = 1;
    private static final int LOG_LIMIT = 100;

    private final ExchangeRateMapper rateMapper;
    private final ExchangeRateLogMapper logMapper;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;

    @Override
    public List<ExchangeRateVO> list() {
        Map<String, ExchangeRateDO> rows = rateMapper.selectList(new LambdaQueryWrapper<ExchangeRateDO>()
                        .eq(ExchangeRateDO::getTenantId, tenantId()))
                .stream().collect(Collectors.toMap(ExchangeRateDO::getCurrencyCode, Function.identity()));
        Map<Long, String> names = lookups.userNames(rows.values().stream().map(ExchangeRateDO::getUpdatedById).toList());
        List<ExchangeRateVO> list = new ArrayList<>(CURRENCIES.size());
        for (String code : CURRENCIES) {
            ExchangeRateDO r = rows.get(code);
            ExchangeRateVO vo = new ExchangeRateVO();
            vo.setCurrencyCode(code);
            if (r != null) {
                vo.setRate(r.getRate());
                vo.setSourceName("手动录入");
                vo.setUpdatedByName(names.get(r.getUpdatedById()));
                vo.setRateTime(r.getRateTime());
            }
            list.add(vo);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExchangeRateVO save(String currencyCode, BigDecimal rate) {
        String code = requireCurrency(currencyCode);
        if (rate == null || rate.signum() <= 0) {
            throw new BizException("汇率需要大于 0");
        }
        if (rate.stripTrailingZeros().scale() > SCALE) {
            throw new BizException("汇率最多 6 位小数");
        }
        if (rate.compareTo(MAX_RATE) >= 0) {
            throw new BizException("汇率数值过大，请检查");
        }
        BigDecimal newRate = rate.setScale(SCALE);
        Long operator = currentUser.resolve() == null ? 0L : currentUser.resolve();
        LocalDateTime now = LocalDateTime.now();
        ExchangeRateDO row = rateMapper.selectOne(new LambdaQueryWrapper<ExchangeRateDO>()
                .eq(ExchangeRateDO::getTenantId, tenantId())
                .eq(ExchangeRateDO::getCurrencyCode, code));
        BigDecimal oldRate = row == null ? null : row.getRate();
        if (oldRate != null && oldRate.compareTo(newRate) == 0) {
            return list().stream().filter(v -> v.getCurrencyCode().equals(code)).findFirst().orElseThrow();
        }
        if (row == null) {
            row = new ExchangeRateDO();
            row.setTenantId(tenantId());
            row.setCurrencyCode(code);
            row.setStatus(1);
        }
        row.setRate(newRate);
        row.setSource(SOURCE_MANUAL);
        row.setUpdatedById(operator);
        row.setRateTime(now);
        if (row.getId() == null) {
            rateMapper.insert(row);
        } else {
            rateMapper.updateById(row);
        }
        ExchangeRateLogDO logRow = new ExchangeRateLogDO();
        logRow.setTenantId(tenantId());
        logRow.setCurrencyCode(code);
        logRow.setOldRate(oldRate);
        logRow.setNewRate(newRate);
        logRow.setOperatorId(operator);
        logRow.setOperatedAt(now);
        logMapper.insert(logRow);
        logService.recordOperateLog("汇率设置", "修改汇率",
                oldRate == null ? null : Map.of("currency", code, "rate", oldRate.toPlainString()),
                Map.of("currency", code, "rate", newRate.toPlainString()));
        log.info("系统汇率更新，tenantId={}, currency={}, old={}, new={}", tenantId(), code, oldRate, newRate);
        return list().stream().filter(v -> v.getCurrencyCode().equals(code)).findFirst().orElseThrow();
    }

    @Override
    public List<ExchangeRateLogVO> logs(String currencyCode) {
        String code = requireCurrency(currencyCode);
        List<ExchangeRateLogDO> rows = logMapper.selectList(new LambdaQueryWrapper<ExchangeRateLogDO>()
                .eq(ExchangeRateLogDO::getTenantId, tenantId())
                .eq(ExchangeRateLogDO::getCurrencyCode, code)
                .orderByDesc(ExchangeRateLogDO::getId)
                .last("LIMIT " + LOG_LIMIT));
        Map<Long, String> names = lookups.userNames(rows.stream().map(ExchangeRateLogDO::getOperatorId).toList());
        return rows.stream().map(r -> {
            ExchangeRateLogVO vo = new ExchangeRateLogVO();
            vo.setCurrencyCode(r.getCurrencyCode());
            vo.setOldRate(r.getOldRate());
            vo.setNewRate(r.getNewRate());
            vo.setOperatorName(names.get(r.getOperatorId()));
            vo.setOperatedAt(r.getOperatedAt());
            return vo;
        }).toList();
    }

    @Override
    public Snapshot require(String currencyCode) {
        String code = currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
        if (BASE_CURRENCY.equals(code)) {
            return new Snapshot(BASE_CURRENCY, BigDecimal.ONE.setScale(SCALE), null);
        }
        ExchangeRateDO row = CURRENCIES.contains(code) ? rateMapper.selectOne(new LambdaQueryWrapper<ExchangeRateDO>()
                .eq(ExchangeRateDO::getTenantId, tenantId())
                .eq(ExchangeRateDO::getCurrencyCode, code)) : null;
        if (row == null || row.getRate() == null || row.getRate().signum() <= 0) {
            throw BizException.of("EXCHANGE_RATE_MISSING", "还没有设置 " + code + " 汇率，请联系管理员在系统管理中设置");
        }
        return new Snapshot(code, row.getRate(), row.getRateTime());
    }

    private static String requireCurrency(String currencyCode) {
        String code = currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCIES.contains(code)) {
            throw new BizException("不支持的币种：" + currencyCode);
        }
        return code;
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
