package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderItemMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 对应任务4.1（客户询盘编号：正常生成/查询失败兜底/唯一键冲突交由调用方重试）
 * 与任务5.1（询盘单编号：有父级/无父级/字母溢出到AA）。
 */
@ExtendWith(MockitoExtension.class)
class InquiryCodeGeneratorTest {

    @Mock
    private CustomerInquiryMapper customerInquiryMapper;
    @Mock
    private InquiryOrderMapper inquiryOrderMapper;
    @Mock
    private InquiryOrderItemMapper inquiryOrderItemMapper;

    private InquiryCodeGenerator generator;
    private String todayPrefix;

    @BeforeEach
    void setUp() {
        generator = new InquiryCodeGenerator(customerInquiryMapper, inquiryOrderMapper, inquiryOrderItemMapper);
        todayPrefix = "IQ" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Test
    void nextCustomerInquiryCode_withNoExistingCode_startsAt001() {
        when(customerInquiryMapper.selectMaxSequenceByPrefix(eq(1), any())).thenReturn(null);
        when(inquiryOrderMapper.selectMaxStandaloneSequence(eq(1), any(), anyInt())).thenReturn(null);

        GeneratedInquiryCode result = generator.nextCustomerInquiryCode(1);

        assertThat(result.getCode()).isEqualTo(todayPrefix + "001");
        assertThat(result.isFallbackUsed()).isFalse();
    }

    @Test
    void nextCustomerInquiryCode_incrementsFromExistingMax() {
        when(customerInquiryMapper.selectMaxSequenceByPrefix(eq(1), any())).thenReturn(3);
        when(inquiryOrderMapper.selectMaxStandaloneSequence(eq(1), any(), anyInt())).thenReturn(null);

        GeneratedInquiryCode result = generator.nextCustomerInquiryCode(1);

        assertThat(result.getCode()).isEqualTo(todayPrefix + "004");
    }

    @Test
    void nextCustomerInquiryCode_sharesSequenceSpaceWithStandaloneOrders() {
        // 客户询盘自身最大为1，但已有一个独立询盘单占用了005，两者共用编号空间应从006继续
        when(customerInquiryMapper.selectMaxSequenceByPrefix(eq(1), any())).thenReturn(1);
        when(inquiryOrderMapper.selectMaxStandaloneSequence(eq(1), any(), anyInt())).thenReturn(5);

        GeneratedInquiryCode result = generator.nextCustomerInquiryCode(1);

        assertThat(result.getCode()).isEqualTo(todayPrefix + "006");
    }

    @Test
    void nextCustomerInquiryCode_whenQueryFails_fallsBackToRandomAndFlagsIt() {
        when(customerInquiryMapper.selectMaxSequenceByPrefix(eq(1), any()))
                .thenThrow(new QueryTimeoutException("db timeout"));

        GeneratedInquiryCode result = generator.nextCustomerInquiryCode(1);

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getCode()).startsWith(todayPrefix);
        assertThat(result.getCode()).hasSize(todayPrefix.length() + 3);
    }

    @Test
    void nextInquiryOrderCodeForParent_firstChild_getsLetterA() {
        when(inquiryOrderMapper.countByCustomerInquiryId(1, 100L)).thenReturn(0);

        GeneratedInquiryCode result = generator.nextInquiryOrderCodeForParent(1, 100L, "IQ20260901003");

        assertThat(result.getCode()).isEqualTo("IQ20260901003A");
    }

    @Test
    void nextInquiryOrderCodeForParent_beyond26Children_overflowsToDoubleLetter() {
        when(inquiryOrderMapper.countByCustomerInquiryId(1, 100L)).thenReturn(26);

        GeneratedInquiryCode result = generator.nextInquiryOrderCodeForParent(1, 100L, "IQ20260901003");

        assertThat(result.getCode()).isEqualTo("IQ20260901003AA");
    }

    @Test
    void nextInquiryOrderCodeForParent_27thChild_isAB() {
        when(inquiryOrderMapper.countByCustomerInquiryId(1, 100L)).thenReturn(27);

        GeneratedInquiryCode result = generator.nextInquiryOrderCodeForParent(1, 100L, "IQ20260901003");

        assertThat(result.getCode()).isEqualTo("IQ20260901003AB");
    }

    @Test
    void nextInquiryOrderCodeForParent_whenQueryFails_fallsBackToRandomLetterAndFlagsIt() {
        when(inquiryOrderMapper.countByCustomerInquiryId(1, 100L))
                .thenThrow(new QueryTimeoutException("db timeout"));

        GeneratedInquiryCode result = generator.nextInquiryOrderCodeForParent(1, 100L, "IQ20260901003");

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getCode()).startsWith("IQ20260901003");
        assertThat(result.getCode()).hasSize("IQ20260901003".length() + 1);
    }

    @Test
    void nextStandaloneInquiryOrderCode_whenQueryFails_fallsBackToRandomAndFlagsIt() {
        when(customerInquiryMapper.selectMaxSequenceByPrefix(eq(1), any()))
                .thenThrow(new QueryTimeoutException("db timeout"));

        GeneratedInquiryCode result = generator.nextStandaloneInquiryOrderCode(1);

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getCode()).startsWith(todayPrefix);
        assertThat(result.getCode()).endsWith("A");
        assertThat(result.getCode()).hasSize(todayPrefix.length() + 3 + 1);
    }

    @Test
    void nextStandaloneInquiryOrderCode_withNoExistingCode_isNNN001WithLetterA() {
        when(customerInquiryMapper.selectMaxSequenceByPrefix(eq(1), any())).thenReturn(null);
        when(inquiryOrderMapper.selectMaxStandaloneSequence(eq(1), any(), anyInt())).thenReturn(null);

        GeneratedInquiryCode result = generator.nextStandaloneInquiryOrderCode(1);

        assertThat(result.getCode()).isEqualTo(todayPrefix + "001A");
    }

    @Test
    void nextItemCode_appendsTwoDigitSequenceToOrderCode() {
        when(inquiryOrderItemMapper.countByInquiryOrderId(1, 200L)).thenReturn(2);

        String itemCode = generator.nextItemCode(1, 200L, "IQ20260901003A");

        assertThat(itemCode).isEqualTo("IQ20260901003A03");
    }
}
