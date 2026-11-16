package com.zhul.erp.modules.masterdata.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 公海客户（spec master-data/customer-pool）：认领前只能查看，不能修改，也不能在上面新建询盘或单据。
 * 客户模块之外直接按 ID 取客户的新建入口（手动销售订单、询盘提交）也要调用，见 audit BUS-086。
 */
public final class CustomerPool {

    private CustomerPool() {
    }

    public static boolean inPool(CustomerDO c) {
        return c != null && Objects.equals(c.getInPool(), 1);
    }

    /** 公海客户抛「在公海，认领后才能操作」 */
    public static void requireClaimed(CustomerDO c) {
        if (inPool(c)) {
            String name = StringUtils.hasText(c.getName()) ? c.getName() : c.getContactName();
            throw new BizException("客户「" + name + "」在公海，认领后才能操作");
        }
    }
}
