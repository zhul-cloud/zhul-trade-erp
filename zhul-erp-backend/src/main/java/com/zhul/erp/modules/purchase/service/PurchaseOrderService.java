package com.zhul.erp.modules.purchase.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.purchase.dto.CancelPurchaseOrderRequest;
import com.zhul.erp.modules.purchase.dto.ConfirmPurchaseOrderRequest;
import com.zhul.erp.modules.purchase.dto.MoveItemsRequest;
import com.zhul.erp.modules.purchase.dto.PurchaseAttachmentFile;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderListVO;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderPageQuery;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderStatsVO;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderVO;
import com.zhul.erp.modules.purchase.dto.SavePurchaseOrderRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    PurchaseOrderVO uploadAttachment(Long id, MultipartFile file);

    PurchaseOrderVO deleteAttachment(Long id, Long attachmentId);

    PurchaseAttachmentFile attachmentFile(Long id, Long attachmentId);
}
