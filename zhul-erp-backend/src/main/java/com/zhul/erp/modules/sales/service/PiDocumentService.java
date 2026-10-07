package com.zhul.erp.modules.sales.service;

import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.quotation.dto.PreviewVO;
import com.zhul.erp.modules.sales.dto.SavePiRequest;

/** PI 对外文件：Excel / PDF / 图片与编辑时实时预览；不含成本与利润 */
public interface PiDocumentService {

    /** format：xlsx / pdf / jpg；versionNo 为空时导出编辑中的版本，没有时导出当前有效版本 */
    TemplateFile export(Long id, Integer versionNo, String format);

    /** 用编辑中的内容（不落库）渲染预览 */
    PreviewVO preview(Long id, SavePiRequest content);
}
