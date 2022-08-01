package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.plugin.transformers.SpringTransformers;
import org.hotswap.agent.util.spring.util.ObjectUtils;


@Plugin(name = "SpringCorePlugin",
        testedVersions = {""},
        expectedVersions = {""},
        supportClass = SpringTransformers.class
)
public class SpringPlugin {
    private static Object beanFactory;

    public static <T> T get(ClassLoader appClassLoader) {
        if (beanFactory != null) {
            return (T) beanFactory;
        }
        Object obj = ObjectUtils.getFromPlugin(appClassLoader, SpringPlugin.class.getName(), "beanFactory");
        if (obj == null) {
            obj = ObjectUtils.getFromPlugin(ClassLoader.getSystemClassLoader(), SpringPlugin.class.getName(), "beanFactory");
        }
        beanFactory = obj;
        return (T) beanFactory;
    }

    public static void set(Object obj) {
        beanFactory = obj;
    }
}
