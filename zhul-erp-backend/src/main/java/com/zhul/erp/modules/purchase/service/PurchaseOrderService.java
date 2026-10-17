package com.zhul.erp.modules.purchase.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.purchase.dto.CancelPurchaseOrderRequest;
import com.zhul.erp.modules.purchase.dto.ConfirmPurchaseOrderRequest;
import com.zhul.erp.modules.purchase.dto.MoveItemsRequest;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderListVO;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderPageQuery;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderStatsVO;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderVO;
import com.zhul.erp.modules.purchase.dto.SavePurchaseOrderRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;

public interface PurchaseOrderService {

    PageResult<PurchaseOrderListVO> page(PurchaseOrderPageQuery q);

    PurchaseOrderStatsVO stats();

    PurchaseOrderVO detail(Long id);

    PurchaseOrderVO save(Long id, SavePurchaseOrderRequest req);

    /** 草稿删除行，数量回到需求；草稿没有行时删除整单，返回 null */
    PurchaseOrderVO removeItems(Long id, List<Long> itemIds);

    /** 草稿行改到其他供应商，返回目标草稿的 ID */
    Long moveItems(Long id, MoveItemsRequest req);

    PurchaseOrderVO confirm(Long id, ConfirmPurchaseOrderRequest req);

    PurchaseOrderVO cancel(Long id, CancelPurchaseOrderRequest req);

    void deleteDraft(Long id);

    /** 线上店铺转为供应商：该店铺的草稿、已下单采购订单与建议这家店的需求都改为指向新供应商 */
    PurchaseOrderVO convertShop(Long id);

    PurchaseOrderVO uploadAttachment(Long id, MultipartFile file);

    PurchaseOrderVO deleteAttachment(Long id, Long attachmentId);

    /** 合同的临时访问链接：download=true 时强制下载，否则预览 */
    FileLink attachmentLink(Long id, Long attachmentId, boolean download);

    /** 登记已开票：实际开票日期、电子发票，可按实际发票调整开票金额与税点 */
    PurchaseOrderVO registerInvoice(Long id, com.zhul.erp.modules.purchase.dto.RegisterInvoiceRequest req);

    /** 撤销已开票，回到待开票 */
    PurchaseOrderVO revokeInvoice(Long id, String reason);
}
