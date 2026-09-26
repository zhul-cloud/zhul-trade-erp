package com.zhul.erp.modules.inquiry.customerinquiry.service;

import com.zhul.erp.modules.inquiry.customerinquiry.dto.AttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 客户询盘的图片/Excel附件存储。当前落本地磁盘（{@code zhul.upload.dir} 下的
 * customer-inquiry 子目录），用户已明确后续会迁移到 OSS——迁移时只需替换这个接口的
 * 实现类，{@link com.zhul.erp.modules.inquiry.customerinquiry.service.impl.CustomerInquiryServiceImpl}
 * 和前端都不需要改动（同一套"存储URL + 解析回本地/远程路径"的抽象）。
 */
public interface AttachmentStorageService {

    /** 校验类型/大小并保存图片文件，返回可直接通过浏览器访问的相对URL。 */
    AttachmentVO storeImage(MultipartFile file);

    /** 校验类型/大小并保存Excel/CSV文件，返回可直接通过浏览器访问的相对URL。 */
    AttachmentVO storeExcel(MultipartFile file);

    /** 把 storeImage/storeExcel 返回的相对URL解析回本地磁盘的绝对路径。 */
    Path resolveToAbsolutePath(String url);

    /** 用 Apache POI 读取Excel/CSV文件的全部行，拼成一段文本供AI解析使用。 */
    String extractExcelText(Path absolutePath);
}
