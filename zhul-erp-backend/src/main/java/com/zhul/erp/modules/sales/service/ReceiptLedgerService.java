package com.zhul.erp.modules.sales.service;

import com.zhul.erp.modules.sales.dto.ReceiptRecordPageVO;
import com.zhul.erp.modules.sales.dto.ReceiptRecordQuery;
import com.zhul.erp.modules.sales.dto.ReceiptRowVO;
import com.zhul.erp.modules.sales.dto.UnclaimedListVO;
import com.zhul.erp.modules.sales.dto.UnclaimedReceiptRequest;

/** 收款管理：未认领到账（登记、取消认领、作废）与收款记录统计 */
public interface ReceiptLedgerService {

    UnclaimedListVO unclaimed(String keyword, String currencyCode);

    ReceiptRowVO registerUnclaimed(UnclaimedReceiptRequest req);

    void unclaim(Long id, String reason);

    void voidUnclaimed(Long id, String reason);

    ReceiptRecordPageVO records(ReceiptRecordQuery q);
}
