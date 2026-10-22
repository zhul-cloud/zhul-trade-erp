package com.zhul.erp.modules.workbench.widget;

import com.zhul.erp.common.exception.BizException;

import java.util.List;

/**
 * 工作台看板组件（spec system/workbench「工作台模板与看板组件」）。
 * 新增组件 = 新增一个实现这个接口的 Spring Bean，编码与前端组件注册表一致，模板里引用编码即可，不改框架。
 */
public interface WorkbenchWidget {

    /** 组件编码，模板里按它引用 */
    String key();

    /** 需要的菜单路径，有其中任一个即可显示 */
    List<String> menus();

    /** 组件数据；数据范围由调用的业务服务按当前用户的数据范围处理。只用现有接口取数的组件（前端直接调用）不实现 */
    default Object load() {
        throw new BizException("这个组件没有工作台数据接口");
    }
}
