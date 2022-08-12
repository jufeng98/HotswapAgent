package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.plugin.transformers.SpringTransformers;
import org.hotswap.agent.util.spring.util.ObjectUtils;


@Plugin(name = "SpringCorePlugin", testedVersions = {""}, expectedVersions = {""}, supportClass = SpringTransformers.class)
public class SpringPlugin {
    private static Object beanFactory;
    private static Object applicationContext;

    public static <T> T getBeanFactory() {
        if (beanFactory != null) {
            return (T) beanFactory;
        }
        beanFactory = ObjectUtils.getStaticFieldValue(SpringPlugin.class.getName(), "beanFactory");
        return (T) beanFactory;
    }

    public static <T> T getApplicationContext() {
        if (applicationContext != null) {
            return (T) applicationContext;
        }
        applicationContext = ObjectUtils.getStaticFieldValue(SpringPlugin.class.getName(), "applicationContext");
        return (T) applicationContext;
    }

    public static void setBeanFactory(Object beanFactory) {
        SpringPlugin.beanFactory = beanFactory;
    }

    public static void setApplicationContext(Object applicationContext) {
        SpringPlugin.applicationContext = applicationContext;
    }
}
