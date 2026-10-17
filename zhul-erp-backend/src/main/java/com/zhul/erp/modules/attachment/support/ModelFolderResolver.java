package com.zhul.erp.modules.attachment.support;

import java.util.Optional;

/**
 * 按「品牌 / 型号」归档的附件：上传时给出所属单据，由单据所在模块告诉存储这张单据对应的品牌与型号
 * （如实拍任务）。附件模块不反过来依赖各业务模块。
 */
public interface ModelFolderResolver {

    /** 处理的所属单据类型，如 SHOOT */
    String ownerType();

    /** 本租户这张单据的品牌与型号；找不到时为空，文件放到未归类目录 */
    Optional<Model> modelOf(Long ownerId, int tenantId);

    record Model(String brand, String model) {
    }
}
