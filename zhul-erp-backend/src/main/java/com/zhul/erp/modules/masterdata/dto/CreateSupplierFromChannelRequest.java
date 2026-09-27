package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 从电商询价渠道（淘宝/1688/闲鱼等）创建正式供应商。
 * 对应 inquiry_order_supplier 的"转为正式供应商"动作，channelName 取自该渠道的
 * 店铺/卖家名称，预填为供应商名称，其余字段留空待补充。
 */
@Data
public class CreateSupplierFromChannelRequest {
    @NotBlank(message = "店铺/卖家名称不能为空")
    private String channelName;
    /** 名称与已有供应商重复时，是否仍然强制新建（默认 false：先返回重复提示，不新建） */
    private boolean force = false;
    /** 主营品牌名（可选），按全部品类记录；名称或别名匹配不上的保存为待确认品牌 */
    private List<String> brandNames;
}
