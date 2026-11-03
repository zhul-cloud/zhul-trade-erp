package com.zhul.erp.modules.masterdata.customerprofile.support;

import java.util.List;

/** 客户列表、导出用到的完整度位与经营等级名称（与 ProfileField、RfmCalculator 一致） */
public final class CustomerProfileBits {

    public static final int ALL_DONE = ProfileField.ALL_DONE;
    public static final List<String> GRADE_NAMES = RfmCalculator.GRADE_NAMES;

    private CustomerProfileBits() {
    }
}
