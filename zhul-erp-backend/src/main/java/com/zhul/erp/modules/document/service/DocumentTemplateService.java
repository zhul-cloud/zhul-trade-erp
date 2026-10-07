package com.zhul.erp.modules.document.service;

import com.zhul.erp.modules.document.dto.SaveTextTemplateRequest;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.document.dto.TemplateTypeVO;
import com.zhul.erp.modules.document.dto.TemplateVersionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 单据模版：按租户维护，平台内置 V1 兜底；每类只有一个默认版本 */
public interface DocumentTemplateService {

    List<TemplateTypeVO> types();

    List<TemplateVersionVO> versions(int docType);

    TemplateVersionVO upload(int docType, MultipartFile file, String note);

    TemplateVersionVO saveText(SaveTextTemplateRequest req);

    void setDefault(Long versionId);

    void setEnabled(Long versionId, boolean enabled);

    TemplateFile download(Long versionId);

    /** 用示例数据按指定版本导出（format：xlsx / pdf），设为默认前检查效果 */
    TemplateFile previewExport(Long versionId, String format);

    /** 当前租户某类单据的默认版本（Excel 内容或文字内容） */
    Resolved resolveDefault(int docType);

    record Resolved(Long versionId, int versionNo, byte[] file, String text) {
    }
}
