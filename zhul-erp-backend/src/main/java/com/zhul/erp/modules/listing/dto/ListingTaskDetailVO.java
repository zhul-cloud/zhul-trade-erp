package com.zhul.erp.modules.listing.dto;

import com.zhul.erp.modules.media.dto.MediaItemVO;
import com.zhul.erp.modules.product.content.dto.ProductContentVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 发品任务详情：任务、本公司的营销内容（复制助手）、发品素材 */
@Data
public class ListingTaskDetailVO {
    private ListingTaskVO task;
    private ProductContentVO content;
    /** 本型号用于发品的图片和视频 */
    private List<MediaItemVO> ownMedia = new ArrayList<>();
    /** 选了参考素材时，参考型号的可发客户图片和视频 */
    private List<MediaItemVO> referenceMedia = new ArrayList<>();
    /** 独立站任务的站点信息（其他平台为空） */
    private SiteInfo site;

    /** 站点发布方式、确认上线时的清缓存说明、上线链接 */
    @Data
    public static class SiteInfo {
        private Long siteId;
        private String name;
        private boolean autoPublish;
        private boolean needPurge;
        private String purgeNote;
        /** 语言代码 → 上线链接 */
        private java.util.Map<String, String> liveUrls = new java.util.LinkedHashMap<>();
    }
}
