package org.hotswap.agent.plugin.dubbo.utils;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.dubbo.config.spring.ReferenceBean;
import com.alibaba.dubbo.config.spring.ServiceBean;
import com.alibaba.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import com.alibaba.dubbo.config.spring.beans.factory.annotation.ServiceAnnotationBeanPostProcessor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.DubboRefreshCommands;
import org.hotswap.agent.plugin.spring.SpringCorePlugin;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

public class DubboHotswapUtils {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboHotswapUtils.class);

    public static void replaceReferenceField(Class<?> clz, Field declaredField, Reference reference) throws Exception {
        declaredField.setAccessible(true);
        Object targetBean = getTargetBean(clz);
        ReferenceAnnotationBeanPostProcessor processor = getTargetBean(ReferenceAnnotationBeanPostProcessor.class);
        Method method = processor.getClass().getDeclaredMethod("buildReferencedBeanName", Reference.class, Class.class);
        String name = ReflectionUtils.invokeMethod(method, processor, reference, declaredField.getType());
        name = name + ":hotswap";

        ConcurrentMap<String, ReferenceBean<?>> referenceBeanCache = ReflectionUtils.getField("referenceBeanCache", processor);
        referenceBeanCache.remove(name);

        method = processor.getClass().getDeclaredMethod("buildReferenceBeanIfAbsent",
                String.class, Reference.class, Class.class, ClassLoader.class);
        ReferenceBean<?> referenceBean = ReflectionUtils.invokeMethod(method, processor,
                name, reference, declaredField.getType(), DubboRefreshCommands.class.getClassLoader());

        method = processor.getClass().getDeclaredMethod("buildProxy", String.class, ReferenceBean.class, Class.class);
        Object proxy = ReflectionUtils.invokeMethod(method, processor, name, referenceBean, declaredField.getType());

        ReflectionUtils.setField(declaredField, targetBean, proxy);
        LOGGER.info("refresh dubbo reference field:{}#{} success", clz.getName(), declaredField.getName());
    }

    public static void replaceReferenceXml(String id, String interfaceName, String newUrl) throws Exception {
        BeanDefinition beanDefinition = getBeanDefinition(id);
        if (beanDefinition == null) {
            beanDefinition = getBeanDefinition(interfaceName);
        }
        ReferenceBean<?> referenceBean = new ReferenceBean<>();
        referenceBean.setApplicationContext(SpringCorePlugin.getApplicationContext());
        BeanWrapper beanWrapper = new BeanWrapperImpl(referenceBean);
        List<PropertyValue> propertyValueList = beanDefinition.getPropertyValues().getPropertyValueList();
        for (PropertyValue propertyValue : propertyValueList) {
            beanWrapper.setPropertyValue(propertyValue);
        }
        referenceBean.setUrl(newUrl);
        referenceBean.afterPropertiesSet();
        Object proxy = referenceBean.getObject();
        Object newHandler = ReflectionUtils.getField("handler", Objects.requireNonNull(proxy));

        ConfigurableListableBeanFactory beanFactory = SpringCorePlugin.getBeanFactory();
        Object originalProxy;
        try {
            originalProxy = beanFactory.getBean(id);
        } catch (Exception e) {
            originalProxy = beanFactory.getBean(interfaceName);
        }
        ReflectionUtils.setField("handler", originalProxy, newHandler);
        LOGGER.info("refresh dubbo xml reference:{}@{} success", interfaceName, id);
    }

    public static void replaceServiceBean(Class<?> clz, ServiceBean<?> oldBean, Service service) {
        try {
            ServiceBean<Object> serviceBean = new ServiceBean<>();
            ApplicationContext applicationContext = SpringCorePlugin.getApplicationContext();
            serviceBean.setApplicationContext(applicationContext);
            serviceBean.setApplicationEventPublisher(applicationContext);

            ServiceAnnotationBeanPostProcessor processor = getTargetBean(ServiceAnnotationBeanPostProcessor.class);
            ConfigurableListableBeanFactory beanFactory = SpringCorePlugin.getBeanFactory();
            String beanName = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, clz)[0];

            BeanWrapper beanWrapper = new BeanWrapperImpl(serviceBean);
            Method method = processor.getClass().getDeclaredMethod("buildServiceBeanDefinition", Service.class, Class.class, String.class);
            BeanDefinition beanDefinition = ReflectionUtils.invokeMethod(method, processor, service, Class.forName(oldBean.getInterface()), beanName);
            for (PropertyValue propertyValue : beanDefinition.getPropertyValues().getPropertyValueList()) {
                beanWrapper.setPropertyValue(propertyValue);
            }
            serviceBean.setBeanName(oldBean.getBeanName());
            serviceBean.setRef(oldBean.getRef());

            serviceBean.afterPropertiesSet();
            serviceBean.export();
            oldBean.unexport();
            LOGGER.info("refresh dubbo service {} success", oldBean.getId());
        } catch (Exception e) {
            LOGGER.error("refresh dubbo service error:{}", e, oldBean.getId());
        }
    }

    public static BeanDefinition getBeanDefinition(String id) {
        ConfigurableListableBeanFactory beanFactory = SpringCorePlugin.getBeanFactory();
        BeanDefinition beanDefinition = null;
        try {
            beanDefinition = beanFactory.getBeanDefinition(id);
        } catch (Exception ignored) {
        }
        try {
            ConfigurableListableBeanFactory parentBeanFactory = (ConfigurableListableBeanFactory) beanFactory.getParentBeanFactory();
            if (parentBeanFactory != null) {
                beanDefinition = parentBeanFactory.getBeanDefinition(id);
            }
        } catch (Exception ignored) {
        }
        return beanDefinition;
    }

    public static <T> T getTargetBean(Class<T> clz) {
        ConfigurableListableBeanFactory beanFactory = SpringCorePlugin.getBeanFactory();
        Object target = beanFactory.getBean(clz);
        if (AopUtils.isAopProxy(target) || AopUtils.isJdkDynamicProxy(target)) {
            target = AopProxyUtils.getSingletonTarget(target);
        }
        return (T) target;
    }
}
