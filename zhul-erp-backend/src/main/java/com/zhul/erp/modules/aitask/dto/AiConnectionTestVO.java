package com.zhul.erp.modules.aitask.dto;

import lombok.Data;

import java.util.List;

/** 测试连接结果：逐项显示，失败原因不含 Key */
@Data
public class AiConnectionTestVO {
    private boolean ok;
    private List<Item> items;

    @Data
    public static class Item {
        private String name;
        private boolean ok;
        private String message;
    }
}
