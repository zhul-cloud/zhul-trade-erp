package com.zhul.erp.modules.system.constants;

/** 单据类型代码（编号中的类型部分）；external 为对外单据，编号带租户前缀 */
public enum DocumentType {
    IQ(false), QT(true), PI(true), SO(false), PO(true), SD(false), GR(false), SH(false), CI(true), PL(true), DN(true), CN(true), ST(true);

    private final boolean external;

    DocumentType(boolean external) {
        this.external = external;
    }

    public boolean external() {
        return external;
    }
}
