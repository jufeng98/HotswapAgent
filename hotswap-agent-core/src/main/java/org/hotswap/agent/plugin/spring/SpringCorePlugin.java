package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.plugin.transformers.SpringCoreTransformers;
import org.hotswap.agent.util.spring.util.ObjectUtils;


@Plugin(name = "SpringCorePlugin", testedVersions = {""}, expectedVersions = {""}, supportClass = SpringCoreTransformers.class)
public class SpringCorePlugin {
    private static Object beanFactory;
    private static Object applicationContext;

    public static <T> T getBeanFactory() {
        if (beanFactory != null) {
            return (T) beanFactory;
        }
        beanFactory = ObjectUtils.getStaticFieldValue(SpringCorePlugin.class.getName(), "beanFactory");
        return (T) beanFactory;
    }

    public static <T> T getApplicationContext() {
        if (applicationContext != null) {
            return (T) applicationContext;
        }
        applicationContext = ObjectUtils.getStaticFieldValue(SpringCorePlugin.class.getName(), "applicationContext");
        return (T) applicationContext;
    }

    public static void setBeanFactory(Object beanFactory) {
        SpringCorePlugin.beanFactory = beanFactory;
    }

    public static void setApplicationContext(Object applicationContext) {
        SpringCorePlugin.applicationContext = applicationContext;
    }
}
