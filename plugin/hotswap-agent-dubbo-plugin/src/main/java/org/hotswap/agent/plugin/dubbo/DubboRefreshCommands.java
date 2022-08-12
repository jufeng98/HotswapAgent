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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import static org.hotswap.agent.plugin.dubbo.DubboPlugin.getMapFromPlugin;

/**
 * @author yudong
 */
public class DubboRefreshCommands {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboRefreshCommands.class);

    public static void reloadConfiguration() {
        Map<String, String> absolutePaths = new HashMap<>();
        try {
            absolutePaths = getMapFromPlugin("absolutePaths");
            refreshPropertiesChange(absolutePaths);
        } catch (Exception e) {
            absolutePaths.clear();
            LOGGER.error("reloadConfiguration error", e);
        }

        Map<String, Class<?>> clazzes = new HashMap<>();
        try {
            clazzes = getMapFromPlugin("clazzes");
            refreshClassChange(clazzes);
        } catch (Exception e) {
            clazzes.clear();
            LOGGER.error("reloadConfiguration class error", e);
        }
    }

    private static void refreshClassChange(Map<String, Class<?>> clazzes) throws Exception {
        Iterator<Map.Entry<String, Class<?>>> iterator = clazzes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Class<?>> entry = iterator.next();
            LOGGER.debug("refresh class:{}", entry.getKey());
            Class<?> clz = entry.getValue();
            for (Field declaredField : clz.getDeclaredFields()) {
                Reference reference = declaredField.getAnnotation(Reference.class);
                if (reference != null) {
                    DubboHotswapUtils.replaceReferenceField(clz, declaredField, reference);
                }
            }

            Service service = clz.getAnnotation(Service.class);
            if (service != null) {
                Map<String, Object> serviceBeans = getMapFromPlugin("serviceBeans");
                for (Class<?> anInterface : clz.getInterfaces()) {
                    ServiceBean<?> oldServiceBean = (ServiceBean<?>) serviceBeans.get(anInterface.getName());
                    if (oldServiceBean != null) {
                        DubboHotswapUtils.replaceServiceBean(clz, oldServiceBean, service);
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
        paths.clear();
    }

}
