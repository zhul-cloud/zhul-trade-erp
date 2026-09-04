package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderItemMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 询盘业务单据编号生成器，沿用飞书原方案的"日期+流水号"惯例，见
 * openspec/changes/add-inquiry-management/design.md 决策9：
 * <pre>
 * 客户询盘编号：   IQ{YYYYMMDD}{NNN}
 * 询盘单编号：     {客户询盘编号}{字母}（无父级时 IQ{YYYYMMDD}{NNN}{字母}）
 * 明细编号：       {询盘单编号}{NN}
 * </pre>
 * 无父级客户询盘的"独立询盘单"与"客户询盘"共用同一个 IQ{日期}{NNN} 编号空间——
 * 两者各自独立累加会导致同一个 NNN 既被客户询盘占用、又被某个独立询盘单占用，
 * 一旦该客户询盘后续拆单产生编号相同的子询盘单（同样是 NNN+第一个字母），会与
 * 已存在的独立询盘单编号在 inquiry_order 表内撞车（唯一键冲突）。因此两个生成
 * 方法在查询"当日最大流水号"时都会同时查两张表，取较大值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InquiryCodeGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CustomerInquiryMapper customerInquiryMapper;
    private final InquiryOrderMapper inquiryOrderMapper;
    private final InquiryOrderItemMapper inquiryOrderItemMapper;

    /** 客户询盘编号：IQ{YYYYMMDD}{NNN} */
    public GeneratedInquiryCode nextCustomerInquiryCode(int tenantId) {
        String prefix = "IQ" + LocalDate.now().format(DATE_FORMAT);
        try {
            int next = maxDailySequence(tenantId, prefix) + 1;
            return new GeneratedInquiryCode(prefix + pad3(next), false);
        } catch (DataAccessException e) {
            log.warn("查询客户询盘当日最大流水号失败，使用随机数兜底，tenantId={}", tenantId, e);
            return new GeneratedInquiryCode(prefix + pad3(randomSequence()), true);
        }
    }

    /** 有父级客户询盘时的询盘单编号：{客户询盘编号}{字母}，字母按该客户询盘下已有询盘单数量递增。 */
    public GeneratedInquiryCode nextInquiryOrderCodeForParent(int tenantId, Long customerInquiryId, String parentInquiryCode) {
        try {
            int existingCount = inquiryOrderMapper.countByCustomerInquiryId(tenantId, customerInquiryId);
            return new GeneratedInquiryCode(parentInquiryCode + letterSuffix(existingCount), false);
        } catch (DataAccessException e) {
            log.warn("查询询盘单同级数量失败，使用随机字母兜底，customerInquiryId={}", customerInquiryId, e);
            return new GeneratedInquiryCode(parentInquiryCode + letterSuffix(ThreadLocalRandom.current().nextInt(0, 26)), true);
        }
    }

    /** 无父级（手动创建）时的独立询盘单编号：IQ{YYYYMMDD}{NNN}A（固定字母A，形状与AI拆单子编号一致）。 */
    public GeneratedInquiryCode nextStandaloneInquiryOrderCode(int tenantId) {
        String prefix = "IQ" + LocalDate.now().format(DATE_FORMAT);
        try {
            int next = maxDailySequence(tenantId, prefix) + 1;
            return new GeneratedInquiryCode(prefix + pad3(next) + "A", false);
        } catch (DataAccessException e) {
            log.warn("查询独立询盘单当日最大流水号失败，使用随机数兜底，tenantId={}", tenantId, e);
            return new GeneratedInquiryCode(prefix + pad3(randomSequence()) + "A", true);
        }
    }

    /** 询盘单明细编号：{询盘单编号}{NN}，NN 为该询盘单下已有明细数量+1，从01开始。 */
    public String nextItemCode(int tenantId, Long inquiryOrderId, String orderCode) {
        int existingCount = inquiryOrderItemMapper.countByInquiryOrderId(tenantId, inquiryOrderId);
        return orderCode + pad2(existingCount + 1);
    }

    /**
     * 字母后缀：0->A ... 25->Z, 26->AA, 27->AB ...（经典 Excel 列名算法）。
     * index 为"已存在的同级数量"（0-based）。
     */
    public static String letterSuffix(int index) {
        int n = index + 1; // 转成 1-based
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private int maxDailySequence(int tenantId, String prefix) {
        Integer fromCustomerInquiry = customerInquiryMapper.selectMaxSequenceByPrefix(tenantId, prefix);
        Integer fromStandaloneOrder = inquiryOrderMapper.selectMaxStandaloneSequence(tenantId, prefix, prefix.length() + 1);
        int a = fromCustomerInquiry != null ? fromCustomerInquiry : 0;
        int b = fromStandaloneOrder != null ? fromStandaloneOrder : 0;
        return Math.max(a, b);
    }

    private static String pad3(int n) {
        return String.format("%03d", n);
    }

    private static String pad2(int n) {
        return String.format("%02d", n);
    }

    private static int randomSequence() {
        return ThreadLocalRandom.current().nextInt(1, 1000);
    }
}
