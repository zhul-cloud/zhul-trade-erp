package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

/** 货代（服务商） */
@Data
public class ForwarderVO {
    private Long id;
    private String name;
    private Integer volumeDivisor;
}
