package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 询盘编号：客户询盘 IQ{YYYYMMDD}{NNN}；询价任务为 {客户询盘编号}{字母}，字母按确认时的分组顺序 A、B、C…
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InquiryCodeGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CustomerInquiryMapper customerInquiryMapper;

    /** 客户询盘编号：IQ{YYYYMMDD}{NNN}；查询失败时用随机流水号兜底，由调用方在唯一键冲突时重试 */
    public GeneratedInquiryCode nextCustomerInquiryCode(int tenantId) {
        String prefix = "IQ" + LocalDate.now().format(DATE_FORMAT);
        try {
            Integer max = customerInquiryMapper.selectMaxSequenceByPrefix(tenantId, prefix);
            return new GeneratedInquiryCode(prefix + String.format("%03d", (max == null ? 0 : max) + 1), false);
        } catch (DataAccessException e) {
            log.warn("查询客户询盘当日最大流水号失败，使用随机数兜底，tenantId={}", tenantId, e);
            return new GeneratedInquiryCode(prefix + String.format("%03d", ThreadLocalRandom.current().nextInt(1, 1000)), true);
        }
    }

    /** 字母后缀：0→A … 25→Z，26→AA（Excel 列名算法），index 从 0 开始 */
    public static String letterSuffix(int index) {
        int n = index + 1;
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }
}
