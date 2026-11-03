package com.zhul.erp.framework.tenant;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按表的隔离方式（{@link TenantTables}）自动追加租户条件：私有表 {@code tenant_id = 当前租户}；
 * 共享表查询为 {@code tenant_id IN (0, 当前租户)}，共享只读表的修改、删除仍是 {@code = 当前租户}。
 * 没有租户上下文又不在 {@link TenantScope#platform} 内时直接报错，不会查到其他租户的数据。
 */
public class TenantInterceptor extends TenantLineInnerInterceptor {

    private static final ThreadLocal<Boolean> WRITING = ThreadLocal.withInitial(() -> false);

    /** 语句末尾的加锁子句（MySQL 只允许出现在末尾） */
    private static final Pattern LOCK_SUFFIX = Pattern.compile(
            "(?is)\\s+(FOR\\s+UPDATE|FOR\\s+SHARE|LOCK\\s+IN\\s+SHARE\\s+MODE)(\\s+(NOWAIT|SKIP\\s+LOCKED))?\\s*$");

    private final boolean enabled;

    public TenantInterceptor(boolean enabled) {
        super(new Handler(enabled));
        this.enabled = enabled;
    }

    /**
     * 只处理涉及已纳入管辖的表的 SQL。父类会把每条 SQL 解析后重新拼接，即使表被忽略也一样，
     * JSqlParser 重新拼接时会把 {@code ORDER BY ... FOR UPDATE} 拼成 {@code FOR UPDATE ORDER BY ...}；
     * 不涉及这些表的 SQL 原样放行。
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        String sql = boundSql.getSql();
        if (!applies(sql)) {
            return;
        }
        // JSqlParser 4.9 重新拼接时会把 ORDER BY / LIMIT 之后的加锁子句挪到它们前面（FOR UPDATE ORDER BY ...），
        // MySQL 报语法错误。加锁子句只能在语句末尾：改写前截下，改写后原样接回
        Matcher lock = LOCK_SUFFIX.matcher(sql);
        if (!lock.find()) {
            super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
            return;
        }
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        mpBs.sql(sql.substring(0, lock.start()));
        super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        mpBs.sql(mpBs.sql() + lock.group());
    }

    /** 测试用：与 beforeQuery 相同的改写（截下加锁子句、改写、接回），不执行 */
    String rewriteQuery(String sql) {
        Matcher lock = LOCK_SUFFIX.matcher(sql);
        if (!lock.find()) {
            return parserSingle(sql, null);
        }
        return parserSingle(sql.substring(0, lock.start()), null) + lock.group();
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        if (applies(PluginUtils.mpStatementHandler(sh).mPBoundSql().sql())) {
            super.beforePrepare(sh, connection, transactionTimeout);
        }
    }

    private boolean applies(String sql) {
        return enabled && !TenantScope.isPlatform() && TenantTables.mentions(sql);
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        writing(() -> super.processUpdate(update, index, sql, obj));
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        writing(() -> super.processDelete(delete, index, sql, obj));
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        TenantTables.Policy policy = TenantTables.policy(table.getName());
        boolean shared = policy == TenantTables.Policy.SHARED_WRITE
                || (policy == TenantTables.Policy.SHARED_READ && !WRITING.get());
        if (!shared || getTenantLineHandler().ignoreTable(table.getName())) {
            return super.buildTableExpression(table, where, whereSegment);
        }
        return new InExpression(getAliasColumn(table),
                new ParenthesedExpressionList<>(new LongValue(0), getTenantLineHandler().getTenantId()));
    }

    private static void writing(Runnable action) {
        WRITING.set(true);
        try {
            action.run();
        } finally {
            WRITING.remove();
        }
    }

    private record Handler(boolean enabled) implements TenantLineHandler {

        @Override
        public Expression getTenantId() {
            Integer tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                throw new IllegalStateException("缺少租户上下文：跨租户的操作须放在 TenantScope.platform 内");
            }
            return new LongValue(tenantId);
        }

        @Override
        public boolean ignoreTable(String tableName) {
            return !enabled || TenantScope.isPlatform() || TenantTables.policy(tableName) == null;
        }
    }
}
