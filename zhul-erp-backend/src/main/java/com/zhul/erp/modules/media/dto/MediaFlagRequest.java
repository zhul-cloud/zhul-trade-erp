package com.zhul.erp.modules.media.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 修改素材标记：只传要改的那个 */
@Data
public class MediaFlagRequest {
    @NotNull
    @Valid
    private MediaRef ref;
    private Boolean canSend;
    private Boolean forListing;
}
