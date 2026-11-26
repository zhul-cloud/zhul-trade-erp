package com.zhul.erp.modules.video.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 素材的顺序与是否选用：按列表顺序排列，须包含任务的全部图片 */
@Data
public class MaterialsRequest {
    @Valid
    @NotNull
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        @NotNull
        private Long attachmentId;
        private boolean used = true;
    }
}
