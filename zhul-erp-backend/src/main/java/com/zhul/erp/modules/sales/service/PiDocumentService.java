package com.zhul.erp.modules.sales.service;

import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.quotation.dto.PreviewVO;
import com.zhul.erp.modules.sales.dto.SavePiRequest;

/** PI 对外文件：Excel / PDF / 图片与编辑时实时预览；不含成本与利润 */
public interface PiDocumentService {

    /** format：xlsx / pdf / jpg；versionNo 为空时导出编辑中的版本，没有时导出当前有效版本 */
    TemplateFile export(Long id, Integer versionNo, String format);

    /** 议价测算表：按版本、测算汇率（空时取 PI 汇率）与整单折扣（按百分比或金额）生成 Excel */
    TemplateFile bargainExport(Long id, Integer versionNo, java.math.BigDecimal rate, Integer discountType, java.math.BigDecimal discountValue);

    /** 用编辑中的内容（不落库）渲染预览 */
    PreviewVO preview(Long id, SavePiRequest content);
}
