package org.hotswap.agent.plugin.dubbo;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.dubbo.config.spring.ServiceBean;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.proxy.ReferenceBeanProxy;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static org.hotswap.agent.util.spring.util.ObjectUtils.getFromPlugin;

/**
 * @author yudong
 */
public class DubboRefreshCommands {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboRefreshCommands.class);
    private static final XmlBeanDefinitionReader definitionReader = new XmlBeanDefinitionReader(new SimpleBeanDefinitionRegistry());

    public static void reloadConfiguration() {
        try {
            Map<String, String> absolutePaths = getMapFromPlugin("absolutePaths");
            refreshXmlChange(absolutePaths);
        } catch (Exception e) {
            LOGGER.error("reloadConfiguration xml error", e);
        }

        try {
            Map<String, CtClass> clazzes = getMapFromPlugin("clazzes");
            refreshClassChange(clazzes);
        } catch (Exception e) {
            LOGGER.error("reloadConfiguration class error", e);
        }
    }

    private static void refreshClassChange(Map<String, CtClass> clazzes) throws Exception {
        Iterator<Map.Entry<String, CtClass>> iterator = clazzes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CtClass> entry = iterator.next();
            LOGGER.debug("refresh class:{}", entry.getKey());
            CtClass ctClass = entry.getValue();
            for (CtField declaredField : ctClass.getDeclaredFields()) {
                Reference reference = (Reference) declaredField.getAnnotation(Reference.class);
                if (reference != null) {
                    ReferenceBeanProxy.refreshReferenceBean(declaredField, ctClass);
                }
            }

            Service service = (Service) ctClass.getAnnotation(Service.class);
            if (service != null) {
                Map<String, Object> serviceBeans = getMapFromPlugin("serviceBeans");
                for (CtClass anInterface : ctClass.getInterfaces()) {
                    ServiceBean<?> serviceBean = (ServiceBean<?>) serviceBeans.get(anInterface.getName());
                    if (serviceBean != null) {
                        rebuildServiceBean(serviceBean, service.version());
                    } else {
                        LOGGER.warning("serviceBean interface:{} not exists", anInterface.getName());
                    }
                }
            }
            iterator.remove();
        }
    }

    public static void refreshXmlChange(Map<String, String> paths) {
        Iterator<Map.Entry<String, String>> iterator = paths.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            int index = entry.getKey().indexOf("classes");
            ClassPathResource resource = new ClassPathResource(entry.getKey().substring(index + "classes".length()));
            LOGGER.debug("refresh xml:{}", resource.getPath());
            BeanDefinitionRegistry registry = definitionReader.getRegistry();
            Map<String, BeanDefinition> beanDefinitionMap = ReflectionUtils.getField("beanDefinitionMap", registry);
            Objects.requireNonNull(beanDefinitionMap).clear();
            definitionReader.loadBeanDefinitions(resource);
            ReferenceBeanProxy.refresh(registry);
            refreshServiceBean(registry);
            iterator.remove();
        }
    }

    public static void refreshServiceBean(BeanDefinitionRegistry registry) {
        Map<String, Object> serviceBeans = getMapFromPlugin("serviceBeans");
        Arrays.stream(registry.getBeanDefinitionNames())
                .forEach(beanDefinitionName -> {
                    BeanDefinition beanDefinition = registry.getBeanDefinition(beanDefinitionName);
                    String id = (String) beanDefinition.getPropertyValues().get("interface");
                    if (serviceBeans.get(id) == null) {
                        LOGGER.warning("serviceBean id:{} not exists", id);
                        return;
                    }
                    ServiceBean<?> oldBean = (ServiceBean<?>) serviceBeans.get(id);
                    String version = (String) beanDefinition.getPropertyValues().get("version");
                    rebuildServiceBean(oldBean, version);
                });
    }

    public static void rebuildServiceBean(ServiceBean<?> oldBean, String version) {
        try {
            ServiceBean<Object> bean = new ServiceBean<>();
            bean.setApplication(oldBean.getApplication());
            bean.setBeanName(oldBean.getBeanName());
            bean.setRegistries(oldBean.getRegistries());
            bean.setTimeout(oldBean.getTimeout());
            bean.setInterface(oldBean.getInterface());
            bean.setRef(oldBean.getRef());
            ReflectionUtils.setField("applicationEventPublisher", bean,
                    ReflectionUtils.getField("applicationEventPublisher", oldBean));
            bean.setVersion(version);
            oldBean.unexport();
            bean.export();
            LOGGER.info("refresh dubbo service {} success", oldBean.getId());
        } catch (Exception e) {
            LOGGER.error("refresh dubbo service error:{}", e, oldBean.getId());
        }
    }

    public static <T> T getMapFromPlugin(String name) {
        Map<?, ?> val = getFromPlugin(DubboRefreshCommands.class.getClassLoader(), DubboPlugin.class.getName(), name);
        if (!val.isEmpty()) {
            return (T) val;
        }
        return getFromPlugin(ClassLoader.getSystemClassLoader(), DubboPlugin.class.getName(), name);
    }

}
