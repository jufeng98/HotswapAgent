package org.hotswap.agent.plugin.feign;

import feign.Client;
import feign.Target;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.SpringPlugin;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static org.hotswap.agent.util.spring.util.ObjectUtils.getFromPlugin;

/**
 * @author yudong
 */
public class FeignRefreshCommands {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(FeignRefreshCommands.class);

    public static void reloadConfiguration() {
        try {
            Map<String, String> absolutePaths = getMapFromPlugin("absolutePaths");
            refreshYmlChange(absolutePaths);
        } catch (Exception e) {
            LOGGER.error("reloadConfiguration yml error", e);
        }
    }


    public static void refreshYmlChange(Map<String, String> paths) {
        Iterator<Map.Entry<String, String>> iterator = paths.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            FileSystemResource resource = new FileSystemResource(entry.getKey());
            LOGGER.debug("refresh yml:{}", resource.getPath());
            YamlPropertiesFactoryBean yamlProcessor = new YamlPropertiesFactoryBean();
            yamlProcessor.setSingleton(true);
            yamlProcessor.setResources(resource);
            yamlProcessor.afterPropertiesSet();
            Properties properties = yamlProcessor.getObject();
            Map<String, Map<String, String>> map = new HashMap<>();
            Objects.requireNonNull(properties).forEach((key, value) -> {
                String k = (String) key;
                int i = k.lastIndexOf(".");
                String service = k.substring(0, i);
                String prop = k.substring(i + 1);
                Map<String, String> mapProp = map.computeIfAbsent(service, k1 -> new HashMap<>());
                mapProp.put(prop, (String) value);
            });

            ConfigurableListableBeanFactory context = SpringPlugin.get(FeignRefreshCommands.class.getClassLoader());
            map.forEach((key, value) -> changeFeignServiceUrl(key, value.get("url"), context));
            iterator.remove();
        }
    }

    public static void changeFeignServiceUrl(String feignName, String newUrl, ConfigurableListableBeanFactory context) {
        try {
            Object feignService = context.getBean(feignName);
            Object hObj = ReflectionUtils.getField("h", feignService);

            HashMap<?, ?> dispatchObj = ReflectionUtils.getField("dispatch", hObj);
            Client client = new Client.Default(null, null);
            for (Object methodHandler : dispatchObj.values()) {
                ReflectionUtils.setField("client", methodHandler, client);
            }

            Target.HardCodedTarget<?> hardCodedTarget = ReflectionUtils.getField("target", hObj);
            ReflectionUtils.setField("url", hardCodedTarget, newUrl);
            LOGGER.info(feignName + " url change to " + newUrl);
        } catch (Exception e) {
            LOGGER.error("{} error", feignName, e);
        }
    }

    public static <T> T getMapFromPlugin(String name) {
        Map<?, ?> val = getFromPlugin(FeignRefreshCommands.class.getClassLoader(), FeignPlugin.class.getName(), name);
        if (!val.isEmpty()) {
            return (T) val;
        }
        return getFromPlugin(ClassLoader.getSystemClassLoader(), FeignPlugin.class.getName(), name);
    }

}
