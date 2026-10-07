package com.zhul.erp.modules.document.constants;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单据模版类型（document_template.doc_type） */
public final class DocTypes {

    public static final int QUOTATION = 1;
    public static final int PI = 2;
    public static final int CI = 3;
    public static final int PL = 4;
    public static final int TEXT_QUOTE = 5;

    public static final List<Integer> ALL = List.of(QUOTATION, TEXT_QUOTE, PI, CI, PL);

    public static final Map<Integer, String> NAMES = Map.of(
            QUOTATION, "报价单 Quotation", PI, "形式发票 PI", CI, "商业发票 CI", PL, "装箱单 PL", TEXT_QUOTE, "文字报价");

    /** 已实现生成的类型；CI / PL 只做模版管理，生成随发货模块 */
    public static final Set<Integer> GENERATABLE = Set.of(QUOTATION, TEXT_QUOTE, PI);

    /** 平台内置模版所在租户 */
    public static final int PLATFORM_TENANT = 0;

    public static final String MODULE = "document-template";
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int TEXT_MAX_LENGTH = 2000;
    public static final String CLASSPATH_PREFIX = "classpath:";

    public static final String PERM_EDIT = "system:document-template:edit";

    private DocTypes() {
    }

    public static boolean isText(int docType) {
        return docType == TEXT_QUOTE;
    }
}
