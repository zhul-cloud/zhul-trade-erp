package com.zhul.erp.modules.system.numbering;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** spec system/document-numbering：编号格式、租户前缀、并发不重号 */
class DocumentNumberServiceTest extends IntegrationTestBase {

    private static final int TENANT = 99301;
    private static final String TODAY = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Autowired private DocumentNumberService service;

    @BeforeEach
    void setUp() {
        cleanup();
        loginAsAdmin("it_num_admin");
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from document_sequence where tenant_id = ?", TENANT);
        jdbc.update("delete from sys_config where tenant_id = ? and config_key = 'document.number-prefix'", TENANT);
    }

    @Test
    void firstOfDay_prefixOnlyOnExternalDocuments() {
        assertThat(service.next(DocumentType.PI)).isEqualTo("PI" + TODAY + "001");
        service.savePrefix("FW");
        assertThat(service.next(DocumentType.PI)).isEqualTo("FWPI" + TODAY + "002");
        assertThat(service.next(DocumentType.QT)).isEqualTo("FWQT" + TODAY + "001");
        assertThat(service.next(DocumentType.SO)).isEqualTo("SO" + TODAY + "001");
        assertThat(service.next(DocumentType.IQ)).isEqualTo("IQ" + TODAY + "001");
    }

    @Test
    void moreThan999Becomes4Digits() {
        service.next(DocumentType.PI);
        jdbc.update("update document_sequence set last_no = 999 where tenant_id = ? and doc_type = 'PI'", TENANT);
        assertThat(service.next(DocumentType.PI)).isEqualTo("PI" + TODAY + "1000");
    }

    @Test
    void prefixValidation_andChangeOnlyAffectsNewNumbers() {
        assertThatThrownBy(() -> service.savePrefix("FW-")).isInstanceOf(BizException.class).hasMessage("单据前缀只能是 2–4 位大写字母");
        assertThatThrownBy(() -> service.savePrefix("F")).hasMessage("单据前缀只能是 2–4 位大写字母");
        assertThatThrownBy(() -> service.savePrefix("FOUWE")).hasMessage("单据前缀只能是 2–4 位大写字母");
        assertThatThrownBy(() -> service.savePrefix("fw")).hasMessage("单据前缀只能是 2–4 位大写字母");
        service.savePrefix("FW");
        String first = service.next(DocumentType.PI);
        service.savePrefix("FOW");
        String second = service.next(DocumentType.PI);
        assertThat(first).startsWith("FWPI");
        assertThat(second).startsWith("FOWPI");
        service.savePrefix("");
        assertThat(service.prefix()).isEmpty();
    }

    @Test
    void concurrentNumbersAreUnique() throws Exception {
        ExecutorService pool = new ThreadPoolExecutor(20, 20, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        List<Callable<String>> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            jobs.add(() -> {
                TenantContext.setTenantId(TENANT);
                try {
                    return service.next(DocumentType.PI);
                } finally {
                    TenantContext.clear();
                }
            });
        }
        Set<String> numbers = new HashSet<>();
        try {
            for (Future<String> f : pool.invokeAll(jobs)) {
                numbers.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertThat(numbers).hasSize(20);
        assertThat(numbers).contains("PI" + TODAY + "001", "PI" + TODAY + "020");
    }
}
