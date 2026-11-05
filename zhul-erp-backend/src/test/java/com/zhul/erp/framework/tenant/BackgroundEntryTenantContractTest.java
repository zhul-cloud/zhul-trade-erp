package com.zhul.erp.framework.tenant;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 没有登录用户的后台入口（定时任务、启动任务、启动完成事件）在没有租户上下文时逐个执行一遍，
 * 不能出现「缺少租户上下文」：既不能抛出，也不能被任务自己捕获后只写进日志（audit SEC-011）。
 * 自动发现本项目所有这类入口，新加的任务不用单独写测试也会被检查；线程池回调等入口反射找不到，仍需单独测试。
 */
class BackgroundEntryTenantContractTest extends IntegrationTestBase {

    private static final String BASE_PACKAGE = "com.zhul.erp.";
    private static final String MISSING = "缺少租户上下文";

    @Autowired private ApplicationContext context;

    @Test
    void everyBackgroundEntryRunsWithoutTenantContext() {
        TreeMap<String, Runnable> entries = discover();
        assertTrue(entries.size() >= 10, "应发现定时任务与启动任务，实际：" + entries.keySet());

        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        List<String> problems = new ArrayList<>();
        for (var e : entries.entrySet()) {
            ListAppender<ILoggingEvent> logs = new ListAppender<>();
            logs.start();
            root.addAppender(logs);
            TenantContext.clear();
            SecurityContextHolder.clearContext();
            try {
                e.getValue().run();
            } catch (RuntimeException ex) {
                if (mentionsMissing(ex)) {
                    problems.add(e.getKey() + " 抛出：" + rootMessage(ex));
                }
            } finally {
                root.detachAppender(logs);
                TenantContext.clear();
            }
            logs.list.stream().filter(BackgroundEntryTenantContractTest::mentionsMissing).findFirst()
                    .ifPresent(ev -> problems.add(e.getKey() + " 日志：" + ev.getFormattedMessage()));
            assertTrue(!TenantScope.isPlatform(), e.getKey() + " 结束后平台范围没有退出");
        }
        assertTrue(problems.isEmpty(), "后台入口缺少租户上下文：" + problems);
    }

    /** 本项目 Bean 上的 @Scheduled、启动完成事件监听，以及 ApplicationRunner / CommandLineRunner */
    private TreeMap<String, Runnable> discover() {
        TreeMap<String, Runnable> out = new TreeMap<>();
        ApplicationArguments noArgs = new DefaultApplicationArguments();
        for (String name : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(name);
            Class<?> type = AopUtils.getTargetClass(bean);
            if (!type.getName().startsWith(BASE_PACKAGE) || type.getName().contains("Test")) {
                continue;
            }
            if (bean instanceof ApplicationRunner r) {
                out.put(type.getSimpleName() + ".run", () -> call(() -> r.run(noArgs)));
            }
            if (bean instanceof CommandLineRunner r) {
                out.put(type.getSimpleName() + ".run", () -> call(() -> r.run()));
            }
            for (Method m : ReflectionUtils.getUniqueDeclaredMethods(type)) {
                if (m.getParameterCount() == 0 && (AnnotatedElementUtils.hasAnnotation(m, Scheduled.class) || onReady(m))) {
                    Method target = AopUtils.selectInvocableMethod(m, bean.getClass());
                    out.put(type.getSimpleName() + "." + m.getName(), () -> invoke(target, bean));
                }
            }
        }
        return out;
    }

    private static boolean onReady(Method m) {
        EventListener l = AnnotatedElementUtils.findMergedAnnotation(m, EventListener.class);
        return l != null && Arrays.asList(l.classes()).contains(ApplicationReadyEvent.class);
    }

    private static void invoke(Method m, Object bean) {
        try {
            ReflectionUtils.makeAccessible(m);
            m.invoke(bean);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    private interface Checked {
        void run() throws Exception;
    }

    private static void call(Checked c) {
        try {
            c.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null) {
            x = x.getCause();
        }
        return x.getMessage();
    }

    private static boolean mentionsMissing(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            if (x.getMessage() != null && x.getMessage().contains(MISSING)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsMissing(ILoggingEvent ev) {
        if (ev.getFormattedMessage() != null && ev.getFormattedMessage().contains(MISSING)) {
            return true;
        }
        for (IThrowableProxy p = ev.getThrowableProxy(); p != null; p = p.getCause()) {
            if (p.getMessage() != null && p.getMessage().contains(MISSING)) {
                return true;
            }
        }
        return false;
    }
}
