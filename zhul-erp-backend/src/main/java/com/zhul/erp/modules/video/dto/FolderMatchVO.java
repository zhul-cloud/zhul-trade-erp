package com.zhul.erp.modules.video.dto;

import com.zhul.erp.modules.product.dto.ProductOptionVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一个子文件夹的匹配结果：matched-已匹配、multiple-有多个商品、none-未匹配 */
@Data
public class FolderMatchVO {
    private String folder;
    private String result;
    private List<ProductOptionVO> products = new ArrayList<>();
    /** 已匹配时：这个商品是否已有视频任务（有则追加） */
    private Boolean hasTask;
}
