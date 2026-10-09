package com.zhul.erp.modules.purchase.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PurchaseRequirementMapper extends BaseMapper<PurchaseRequirementDO> {

    /** 锁住需求行：下单、改数量、拆分在同一需求上串行执行 */
    @Select("<script>SELECT id FROM purchase_requirement WHERE id IN "
            + "<foreach collection='ids' item='x' open='(' separator=',' close=')'>#{x}</foreach> ORDER BY id FOR UPDATE</script>")
    List<Long> lockByIds(@Param("ids") Collection<Long> ids);
}
