package com.zhul.erp.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    /** 租户拦截器须在分页拦截器之前，分页的 count 语句才会带上租户条件 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(@Value("${zhul.tenant.enforce:true}") boolean tenantEnforce) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantInterceptor(tenantEnforce));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "createBy", String.class, SecurityUtils.getCurrentUsername());
                this.strictInsertFill(metaObject, "updateBy", String.class, SecurityUtils.getCurrentUsername());
            }
            /**
             * 更新时总是覆盖：业务代码普遍先查出完整实体再 updateById，实体上带着旧的更新人和更新时间，
             * strictUpdateFill 只填空字段，会让它们原样写回。条件更新（update(wrapper)）需传空实体才会触发本填充。
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
                this.setFieldValByName("updateBy", SecurityUtils.getCurrentUsername(), metaObject);
            }
        };
    }
}
