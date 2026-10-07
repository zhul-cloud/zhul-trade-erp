package com.zhul.erp.modules.quotation.service;

import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.quotation.dto.PreviewRequest;
import com.zhul.erp.modules.quotation.dto.PreviewVO;
import com.zhul.erp.modules.quotation.dto.QuoteTextVO;

/** 报价单对外文件：文字报价、正式报价单（Excel / PDF / 图片）、编辑时实时预览；均不含成本与利润 */
public interface QuotationDocumentService {

    QuoteTextVO text(Long id);

    /** format：xlsx / pdf / jpg */
    TemplateFile export(Long id, String format);

    PreviewVO preview(PreviewRequest req);

    /** PDF / 图片转换服务是否可用 */
    boolean converterAvailable();
}
