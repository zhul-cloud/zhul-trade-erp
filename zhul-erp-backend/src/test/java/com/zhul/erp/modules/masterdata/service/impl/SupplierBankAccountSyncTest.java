package com.zhul.erp.modules.masterdata.service.impl;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.AesUtils;
import com.zhul.erp.modules.masterdata.dto.SupplierBankAccountRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierBankAccountVO;
import com.zhul.erp.modules.masterdata.entity.SupplierBankAccountDO;
import com.zhul.erp.modules.masterdata.repository.SupplierBankAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 对应 specs/master-data/supplier-settlement-account/spec.md */
@ExtendWith(MockitoExtension.class)
class SupplierBankAccountSyncTest {

    @Mock
    private SupplierBankAccountMapper accountMapper;

    private AesUtils aes;
    private SupplierBankAccountSync sync;

    @BeforeEach
    void setUp() {
        aes = new AesUtils();
        ReflectionTestUtils.setField(aes, "secret", "unit-test-key");
        sync = new SupplierBankAccountSync(accountMapper, aes);
    }

    private static SupplierBankAccountRequest account(Long id, int type, boolean isDefault) {
        SupplierBankAccountRequest r = new SupplierBankAccountRequest();
        r.setId(id);
        r.setAccountType(type);
        r.setAccountName(type == 1 ? "上海电子科技有限公司" : "张三");
        r.setBankName("招商银行上海分行");
        r.setAccountNo("6222021234560008888");
        r.setDefaultAccount(isDefault);
        return r;
    }

    private SupplierBankAccountDO existing(long id, int type, int isDefault, String idNoPlain) {
        SupplierBankAccountDO row = new SupplierBankAccountDO();
        row.setId(id);
        row.setTenantId(1);
        row.setSupplierId(9L);
        row.setAccountType(type);
        row.setAccountName("张三");
        row.setBankName("工商银行");
        row.setAccountNo("6212261001011234");
        row.setPayeePhone("13812345678");
        row.setPayeeIdNo(idNoPlain == null ? "" : aes.encrypt(idNoPlain));
        row.setIsDefault(isDefault);
        row.setSortOrder(0);
        return row;
    }

    @Test
    void merge_withoutDefault_makesFirstDefault() {
        when(accountMapper.selectList(any())).thenReturn(List.of());

        sync.merge(1, 9L, List.of(account(null, 2, false), account(null, 1, false)));

        ArgumentCaptor<SupplierBankAccountDO> captor = ArgumentCaptor.forClass(SupplierBankAccountDO.class);
        verify(accountMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SupplierBankAccountDO::getIsDefault).containsExactly(1, 0);
        assertThat(captor.getAllValues()).extracting(SupplierBankAccountDO::getSortOrder).containsExactly(0, 1);
    }

    @Test
    void merge_withTwoDefaults_rejects() {
        BizException e = assertThrows(BizException.class,
                () -> sync.merge(1, 9L, List.of(account(null, 1, true), account(null, 2, true))));
        assertThat(e.getMessage()).isEqualTo("只能有一个默认收款账户");
        verify(accountMapper, never()).insert(any(SupplierBankAccountDO.class));
    }

    @Test
    void merge_withElevenAccounts_rejects() {
        List<SupplierBankAccountRequest> reqs = new ArrayList<>(Collections.nCopies(11, account(null, 1, false)));
        BizException e = assertThrows(BizException.class, () -> sync.merge(1, 9L, reqs));
        assertThat(e.getMessage()).isEqualTo("每个供应商最多 10 个收款账户");
    }

    @Test
    void merge_personalIdNo_encryptedWhenGiven_keptWhenNull_clearedWhenBlank() {
        SupplierBankAccountDO keep = existing(1L, 2, 1, "31010119900101123X");
        SupplierBankAccountDO clear = existing(2L, 2, 0, "31010119900101123X");
        String keepCipher = keep.getPayeeIdNo();
        when(accountMapper.selectList(any())).thenReturn(List.of(keep, clear));

        SupplierBankAccountRequest keepReq = account(1L, 2, true);
        SupplierBankAccountRequest clearReq = account(2L, 2, false);
        clearReq.setPayeeIdNo("");
        SupplierBankAccountRequest newReq = account(null, 2, false);
        newReq.setPayeeIdNo("31010119900101123x");
        sync.merge(1, 9L, List.of(keepReq, clearReq, newReq));

        assertThat(keep.getPayeeIdNo()).isEqualTo(keepCipher);
        assertThat(clear.getPayeeIdNo()).isEmpty();
        ArgumentCaptor<SupplierBankAccountDO> captor = ArgumentCaptor.forClass(SupplierBankAccountDO.class);
        verify(accountMapper).insert(captor.capture());
        String cipher = captor.getValue().getPayeeIdNo();
        assertThat(cipher).doesNotContain("310101");
        assertThat(aes.decrypt(cipher)).isEqualTo("31010119900101123X");
    }

    @Test
    void merge_switchingToCorporate_clearsPayeeInfo() {
        SupplierBankAccountDO row = existing(1L, 2, 1, "31010119900101123X");
        when(accountMapper.selectList(any())).thenReturn(List.of(row));

        sync.merge(1, 9L, List.of(account(1L, 1, true)));

        assertThat(row.getPayeeIdNo()).isEmpty();
        assertThat(row.getPayeePhone()).isEmpty();
    }

    @Test
    void merge_removedAccountIsSoftDeleted_unknownIdRejected() {
        SupplierBankAccountDO a = existing(1L, 1, 1, null);
        SupplierBankAccountDO b = existing(2L, 2, 0, null);
        when(accountMapper.selectList(any())).thenReturn(List.of(a, b));

        sync.merge(1, 9L, List.of(account(2L, 2, false)));

        assertThat(a.getDeletedAt()).isNotNull();
        assertThat(b.getDeletedAt()).isNull();
        assertThat(b.getIsDefault()).isEqualTo(1);

        when(accountMapper.selectList(any())).thenReturn(List.of(b));
        assertThrows(BizException.class, () -> sync.merge(1, 9L, List.of(account(77L, 1, true))));
    }

    @Test
    void load_masksForDetail_plainForForm_idNoAlwaysMasked() {
        when(accountMapper.selectList(any())).thenReturn(List.of(existing(1L, 2, 1, "31010119900101123X")));

        SupplierBankAccountVO masked = sync.load(List.of(9L), false).get(9L).get(0);
        assertThat(masked.getAccountNo()).isEqualTo("6212 **** **** 1234");
        assertThat(masked.getPayeePhone()).isEqualTo("138****5678");
        assertThat(masked.getPayeeIdNoMasked()).isEqualTo("310101********123X");

        SupplierBankAccountVO plain = sync.load(List.of(9L), true).get(9L).get(0);
        assertThat(plain.getAccountNo()).isEqualTo("6212261001011234");
        assertThat(plain.getPayeePhone()).isEqualTo("13812345678");
        assertThat(plain.getPayeeIdNoMasked()).isEqualTo("310101********123X");
    }
}
