package com.zhul.erp.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.util.Collections;
import java.util.Set;

/**
 * 当前用户的数据可见范围（按负责人）。ALL 可见同租户全部数据；其余只可见 ownerIds 中负责人的数据。
 *
 * @param type     范围类型
 * @param selfId   当前用户的 user_basic.id，账号没有对应用户档案时为 null
 * @param ownerIds 可见的负责人 ID（ALL 时为空集，不使用）
 */
public record DataScope(Type type, Long selfId, Set<Long> ownerIds) {

    public enum Type {
        /** 管理员或角色数据权限为「全部」 */
        ALL,
        /** 角色数据权限为「自定义」：配置部门（含下级）的用户 + 自己 */
        CUSTOM,
        /** 「仅本人」「无权限」或未配置角色 */
        SELF,
        /** 非管理员且没有用户档案：什么都看不到 */
        NONE
    }

    public static DataScope all(Long selfId) {
        return new DataScope(Type.ALL, selfId, Collections.emptySet());
    }

    public boolean isAll() {
        return type == Type.ALL;
    }

    public boolean canSee(Long ownerId) {
        return isAll() || (ownerId != null && ownerIds.contains(ownerId));
    }

    /** 给查询追加 owner 条件；范围为空时追加恒假条件 */
    public <T> LambdaQueryWrapper<T> apply(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> ownerColumn) {
        if (isAll()) {
            return wrapper;
        }
        if (ownerIds.isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        return wrapper.in(ownerColumn, ownerIds);
    }
}
