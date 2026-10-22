package com.zhul.erp.modules.workbench.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.workbench.entity.WorkbenchTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkbenchTemplateMapper extends BaseMapper<WorkbenchTemplateDO> {

    /** 账号的全部角色编码：用户的主角色，加上 account_role 里的附加角色 */
    @Select("SELECT u.role_code FROM account a JOIN user_basic u ON u.id = a.user_id WHERE a.id = #{accountId} "
            + "AND u.role_code != '' UNION SELECT role_code FROM account_role WHERE account_id = #{accountId}")
    List<String> roleCodes(@Param("accountId") Integer accountId);
}
