package com.zhul.erp.modules.video.dto;

import com.zhul.erp.modules.media.dto.MediaItemVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 商品档案「本公司素材」：本公司素材库里这个型号的图片素材、视频素材和营销实拍（只读引用，不复制） */
@Data
public class CompanyMediaVO {
    private String brand;
    private String model;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private MediaItemVO media;
        /** 已设为官方素材 */
        private Boolean official;
    }
}
