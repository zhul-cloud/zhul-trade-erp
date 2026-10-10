package com.zhul.erp.modules.inquiry.sourcing.service;

import com.zhul.erp.modules.inquiry.sourcing.dto.ImportConfirmRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportFileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 询价包 Excel：下载（①询盘单 + ②询价单 + 隐藏标识）与导入询价结果 */
public interface SourcingExcelService {

    /** 单个任务返回 xlsx，多个任务返回 zip；proxy 为真时可下载任何人的任务 */
    Download download(List<Long> taskIds, boolean proxy);

    /** 解析上传的询价包，返回预览；不入库 */
    List<ImportFileVO> preview(List<MultipartFile> files, boolean proxy);

    /** 确认入库：每个文件按任务提交，计入回价进度 */
    void confirm(ImportConfirmRequest req, boolean proxy);

    record Download(String fileName, String contentType, byte[] bytes) {
    }
}
