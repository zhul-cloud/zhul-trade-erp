package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryCodeGeneratorTest {

    @Mock
    private CustomerInquiryMapper customerInquiryMapper;

    private InquiryCodeGenerator generator;
    private String todayPrefix;

    @BeforeEach
    void setUp() {
        generator = new InquiryCodeGenerator(customerInquiryMapper);
        todayPrefix = "IQ" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Test
    void firstCodeOfTheDay() {
        when(customerInquiryMapper.selectMaxSequenceByPrefix(eq(1), any())).thenReturn(null);
        GeneratedInquiryCode result = generator.nextCustomerInquiryCode(1);
        assertThat(result.getCode()).isEqualTo(todayPrefix + "001");
        assertThat(result.isFallbackUsed()).isFalse();
    }

    @Test
    void incrementsFromMax() {
        when(customerInquiryMapper.selectMaxSequenceByPrefix(eq(1), any())).thenReturn(41);
        assertThat(generator.nextCustomerInquiryCode(1).getCode()).isEqualTo(todayPrefix + "042");
    }

    @Test
    void fallsBackToRandomWhenQueryFails() {
        when(customerInquiryMapper.selectMaxSequenceByPrefix(eq(1), any())).thenThrow(new QueryTimeoutException("timeout"));
        GeneratedInquiryCode result = generator.nextCustomerInquiryCode(1);
        assertThat(result.getCode()).startsWith(todayPrefix).hasSize(todayPrefix.length() + 3);
        assertThat(result.isFallbackUsed()).isTrue();
    }

    @Test
    void letterSuffix() {
        assertThat(InquiryCodeGenerator.letterSuffix(0)).isEqualTo("A");
        assertThat(InquiryCodeGenerator.letterSuffix(25)).isEqualTo("Z");
        assertThat(InquiryCodeGenerator.letterSuffix(26)).isEqualTo("AA");
    }
}
