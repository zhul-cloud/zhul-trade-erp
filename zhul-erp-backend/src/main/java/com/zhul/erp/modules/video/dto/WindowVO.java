package com.zhul.erp.modules.video.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 夜间生成时段，如 20:00-07:00 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WindowVO {
    private String window;
}
