package com.zhul.erp.modules.system.service;

import com.zhul.erp.modules.system.constants.DocumentType;

/** 全链路单据编号：前缀（仅对外单据）+ 类型 + 年月日 + 当日 3 位流水（超过 999 自然变 4 位） */
public interface DocumentNumberService {

    String CONFIG_PREFIX = "document.number-prefix";

    /** 生成当前租户下一个编号；独立事务，调用方回滚只留下空号 */
    String next(DocumentType type);

    /** 当前租户的单据前缀（可能为空字符串） */
    String prefix();

    /** 设置当前租户的单据前缀：2–4 位大写字母或空；只影响之后新建的单据 */
    String savePrefix(String prefix);
}
