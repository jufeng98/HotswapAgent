package org.hotswap.agent.plugin.dubbo;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.dubbo.config.spring.ServiceBean;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.proxy.ReferenceBeanProxy;
import org.hotswap.agent.util.spring.util.ClassUtils;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static org.hotswap.agent.util.spring.util.ObjectUtils.getFromPlugin;

/**
 * @author yudong
 */
public class DubboRefreshCommands {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboRefreshCommands.class);
    private static final XmlBeanDefinitionReader definitionReader = new XmlBeanDefinitionReader(new SimpleBeanDefinitionRegistry());

    public static void reloadConfiguration() {
        Map<String, String> absolutePaths = new HashMap<>();
        try {
            absolutePaths = getMapFromPlugin("absolutePaths");
            refreshXmlChange(absolutePaths);
        } catch (Exception e) {
            absolutePaths.clear();
            LOGGER.error("reloadConfiguration xml error", e);
        }

        try {
            absolutePaths = getMapFromPlugin("absolutePaths1");
            refreshPropertiesChange(absolutePaths);
        } catch (Exception e) {
            absolutePaths.clear();
            LOGGER.error("reloadConfiguration error", e);
        }

        Map<String, CtClass> clazzes = new HashMap<>();
        try {
            clazzes = getMapFromPlugin("clazzes");
            refreshClassChange(clazzes);
        } catch (Exception e) {
            clazzes.clear();
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

    private static void refreshPropertiesChange(Map<String, String> paths) throws Exception {
        for (Map.Entry<String, String> entry : new HashMap<>(paths).entrySet()) {
            FileSystemResource resource = new FileSystemResource(entry.getKey());
            LOGGER.debug("refresh properties:{}", resource.getPath());
            Properties properties = new Properties();
            try (InputStream inputStream = resource.getInputStream()) {
                properties.load(inputStream);
            }
            for (Map.Entry<Object, Object> innerEntry : properties.entrySet()) {
                String key = innerEntry.getKey().toString();
                if (key.contains("#")) {
                    String[] split = key.split("#");
                    String clsName = split[0];
                    String fieldName = split[1];
                    Class<?> clz = ClassUtils.getClassFromClassloader(clsName, DubboRefreshCommands.class.getClassLoader());
                    Field declaredField = clz.getDeclaredField(fieldName);
                    ReferenceBeanProxy.refreshReferenceBean(clsName, fieldName, declaredField.getType().getName(),
                            declaredField.getAnnotation(Reference.class), innerEntry.getValue().toString());
                } else {
                    String[] split = key.split("@");
                    String interfaceName = split[0];
                    String id = split[1];
                    ReferenceBeanProxy.refresh(interfaceName, id, innerEntry.getValue().toString());
                }
            }
        }
        paths.clear();
    }

    public static void refreshXmlChange(Map<String, String> paths) {
        for (Map.Entry<String, String> entry : new HashMap<>(paths).entrySet()) {
            int index = entry.getKey().indexOf("classes");
            ClassPathResource resource = new ClassPathResource(entry.getKey().substring(index + "classes".length()));
            LOGGER.debug("refresh xml:{}", resource.getPath());
            BeanDefinitionRegistry registry = definitionReader.getRegistry();
            Map<String, BeanDefinition> beanDefinitionMap = ReflectionUtils.getField("beanDefinitionMap", registry);
            Objects.requireNonNull(beanDefinitionMap).clear();
            try {
                definitionReader.loadBeanDefinitions(resource);
            } catch (Exception ignored) {
                return;
            }
            ReferenceBeanProxy.refresh(registry);
            refreshServiceBean(registry);
        }
        paths.clear();
    }

    public static void refreshServiceBean(BeanDefinitionRegistry registry) {
        Map<String, Object> serviceBeans = getMapFromPlugin("serviceBeans");
        Arrays.stream(registry.getBeanDefinitionNames()).forEach(beanDefinitionName -> {
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
            bean.setRegistries(oldBean.getRegistries());
            if (System.getProperty("dubbo.registry.register") != null) {
                boolean register = Boolean.parseBoolean(System.getProperty("dubbo.registry.register"));
                bean.setRegister(register);
            }
            bean.setBeanName(oldBean.getBeanName());
            bean.setTimeout(oldBean.getTimeout());
            bean.setInterface(oldBean.getInterface());
            bean.setRef(oldBean.getRef());
            bean.setVersion(version);
            ReflectionUtils.setField("applicationEventPublisher", bean, ReflectionUtils.getField("applicationEventPublisher", oldBean));
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
