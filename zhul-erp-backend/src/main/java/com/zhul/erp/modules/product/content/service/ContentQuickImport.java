package com.zhul.erp.modules.product.content.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.ContentConfirmVO;
import com.zhul.erp.modules.product.content.dto.ContentFileRequest;
import com.zhul.erp.modules.product.content.dto.ContentPreviewVO;
import com.zhul.erp.modules.product.service.ProductFaqService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 资料与营销一起上传（spec product/product-content「资料与营销一起上传」）：一次接收资料包和营销包，一起解析；
 * 确认时照常逐个写入，资料包写入成功的商品随后自动审核：待审核的技术问答审核通过，资料标为已核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentQuickImport {

    private final ContentImportService importService;
    private final ProductFaqService faqService;
    private final FactTaskService factTaskService;

    public List<ContentPreviewVO> preview(ContentFileRequest req) {
        return importService.preview(req, null);
    }

    public ContentConfirmVO confirm(ContentFileRequest req) {
        ContentConfirmVO result = importService.confirm(req, null);
        Set<Long> products = new LinkedHashSet<>();
        result.getItems().stream()
                .filter(i -> i.isSuccess() && ContentConstants.TYPE_FACTS.equals(i.getType()) && i.getProductId() != null)
                .forEach(i -> products.add(i.getProductId()));
        int faqs = 0;
        int verified = 0;
        for (Long productId : products) {
            faqs += faqService.approveAllPending(productId);
            try {
                factTaskService.verify(productId);
                verified++;
            } catch (BizException e) {
                log.info("自动核对资料跳过，productId={}，原因={}", productId, e.getMessage());
            }
        }
        result.setApprovedFaqs(faqs);
        result.setVerifiedProducts(verified);
        return result;
    }
}
