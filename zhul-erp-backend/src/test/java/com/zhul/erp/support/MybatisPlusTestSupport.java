package com.zhul.erp.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 不启动 Spring 的单元测试里，LambdaUpdateWrapper#set 需要实体的表信息缓存，
 * 生产环境由 MyBatis-Plus 启动时初始化，单元测试要手动初始化一次。
 */
public final class MybatisPlusTestSupport {

    private MybatisPlusTestSupport() {
    }

    public static void initTableInfo(Class<?>... entityClasses) {
        for (Class<?> entityClass : entityClasses) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }
}
