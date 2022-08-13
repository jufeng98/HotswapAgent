package org.hotswap.agent.plugin.dubbo;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.dubbo.config.spring.ServiceBean;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.utils.DubboHotswapUtils;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.springframework.core.io.FileSystemResource;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Properties;

/**
 * @author yudong
 */
public class DubboRefreshCommands {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboRefreshCommands.class);

    public static void reloadAfterClassRedefine(Class<?> clazz, Map<String, Object> serviceBeans) {
        try {
            refreshClassChange(clazz, serviceBeans);
        } catch (Exception e) {
            LOGGER.error("reload error", e);
        }
    }

    public static void reloadPropertiesChange(String absolutePath) {
        try {
            refreshPropertiesChange(absolutePath);
        } catch (Exception e) {
            LOGGER.error("reload error", e);
        }
    }

    private static void refreshClassChange(Class<?> clz, Map<String, Object> serviceBeans) throws Exception {
        LOGGER.debug("refresh class:{}", clz);
        for (Field declaredField : clz.getDeclaredFields()) {
            Reference reference = declaredField.getAnnotation(Reference.class);
            if (reference != null) {
                DubboHotswapUtils.replaceReferenceField(clz, declaredField, reference);
            }
        }

        Service service = clz.getAnnotation(Service.class);
        if (service != null) {
            for (Class<?> anInterface : clz.getInterfaces()) {
                ServiceBean<?> oldServiceBean = (ServiceBean<?>) serviceBeans.get(anInterface.getName());
                if (oldServiceBean != null) {
                    DubboHotswapUtils.replaceServiceBean(clz, oldServiceBean, service);
                }
            }
        }
    }

    private static void refreshPropertiesChange(String absolutePath) throws Exception {
        FileSystemResource resource = new FileSystemResource(absolutePath);
        LOGGER.debug("refresh properties:{}", resource.getPath());
        Properties properties = new Properties();
        try (InputStream inputStream = resource.getInputStream()) {
            properties.load(inputStream);
        }
        for (Map.Entry<Object, Object> innerEntry : properties.entrySet()) {
            String key = innerEntry.getKey().toString();
            String url = innerEntry.getValue().toString();
            if (key.contains("#")) {
                String[] split = key.split("#");
                String clsName = split[0];
                String fieldName = split[1];
                Class<?> clz = Class.forName(clsName);
                Field declaredField = clz.getDeclaredField(fieldName);

                Reference reference = declaredField.getAnnotation(Reference.class);
                InvocationHandler invocationHandler = Proxy.getInvocationHandler(reference);
                Map<String, Object> memberValuesMap = ReflectionUtils.getField("memberValues", invocationHandler);
                memberValuesMap.put("url", url);

                DubboHotswapUtils.replaceReferenceField(clz, declaredField, reference);
            } else {
                String[] split = key.split("@");
                String interfaceName = split[0];
                String id = split[1];
                DubboHotswapUtils.replaceReferenceXml(id, interfaceName, url);
            }
        }
    }

}
