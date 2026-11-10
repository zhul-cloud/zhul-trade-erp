package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.upload.support.UploadScene;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 询盘附件的直传场景：新建、追加、修改询盘都能上传，权限同询盘单菜单（spec inquiry/inquiry-intake「询盘大附件」） */
@Configuration
public class InquiryUploadScenes {

    @Bean
    public UploadScene inquiryAttachmentScene(PermissionChecker perm) {
        return new UploadScene(InquiryConstants.UPLOAD_SCENE_ATTACHMENT, StorageArea.INQUIRY,
                InquiryConstants.ATTACHMENT_EXTS, InquiryConstants.ATTACHMENT_MAX_BYTES,
                InquiryConstants.ATTACHMENT_TOTAL_BYTES, InquiryConstants.ATTACHMENT_TYPE_MESSAGE,
                () -> perm.canAccessMenu(InquiryConstants.MENU_CUSTOMER_INQUIRY));
    }
}
