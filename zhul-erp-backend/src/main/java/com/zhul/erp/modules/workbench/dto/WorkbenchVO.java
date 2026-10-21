package com.zhul.erp.modules.workbench.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 当前用户的工作台：按职级排好的看板，默认打开第一个 */
@Data
public class WorkbenchVO {
    private List<Board> boards = new ArrayList<>();
    private String defaultCode;

    @Data
    public static class Board {
        private String code;
        private String name;
        /** 这个看板的组件都还没上线、或都没有权限时，先显示通用看板的组件 */
        private Boolean fallback;
        private List<Widget> widgets = new ArrayList<>();
    }

    @Data
    public static class Widget {
        private String key;
        /** full-整行、half-半行 */
        private String size;
    }
}
