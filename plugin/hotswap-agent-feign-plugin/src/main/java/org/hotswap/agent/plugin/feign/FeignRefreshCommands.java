package org.hotswap.agent.plugin.feign;

import feign.Client;
import feign.Target;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.SpringPlugin;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.hotswap.agent.util.spring.util.StringUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.io.FileSystemResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static org.hotswap.agent.util.spring.util.ObjectUtils.getFromPlugin;

/**
 * @author yudong
 */
public class FeignRefreshCommands {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(FeignRefreshCommands.class);
    private static final Map<String, Object> ORIGINAL_MAP = new ConcurrentHashMap<>();

    public static void reloadConfiguration() {
        try {
            Map<String, String> absolutePaths = getMapFromPlugin("absolutePaths");
            refreshPropertiesChange(absolutePaths);
        } catch (Exception e) {
            LOGGER.error("reloadConfiguration error", e);
        }
    }


    public static void refreshPropertiesChange(Map<String, String> paths) throws Exception {
        for (Map.Entry<String, String> entry : new HashMap<>(paths).entrySet()) {
            FileSystemResource resource = new FileSystemResource(entry.getKey());
            LOGGER.debug("refresh properties:{}", resource.getPath());
            Properties properties = new Properties();
            try (InputStream inputStream = resource.getInputStream()) {
                properties.load(inputStream);
            }
            ConfigurableListableBeanFactory context = SpringPlugin.get(FeignRefreshCommands.class.getClassLoader());
            properties.forEach((key, value) -> changeFeignServiceUrl(key.toString(), value.toString(), context));
        }
        paths.clear();
    }

    public static void changeFeignServiceUrl(String feignName, String newUrl, ConfigurableListableBeanFactory context) {
        try {
            boolean resetFlag = StringUtils.isEmpty(newUrl);

            Object feignService = context.getBean(feignName);
            Object hObj = ReflectionUtils.getField("h", feignService);

            Map<?, ?> dispatchObj = ReflectionUtils.getField("dispatch", Objects.requireNonNull(hObj));

            Client client;
            if (resetFlag) {
                client = (Client) ORIGINAL_MAP.get(feignName + ":client");
                newUrl = (String) ORIGINAL_MAP.get(feignName + ":url");
            } else {
                client = new Client.Default(null, null);
                newUrl = "http://" + newUrl;
            }
            for (Object methodHandler : Objects.requireNonNull(dispatchObj).values()) {
                Client originalClient = ReflectionUtils.getField("client", methodHandler);
                ORIGINAL_MAP.putIfAbsent(feignName + ":client", originalClient);
                ReflectionUtils.setField("client", methodHandler, client);
            }

            Target.HardCodedTarget<?> hardCodedTarget = ReflectionUtils.getField("target", hObj);
            String originalUrl = ReflectionUtils.getField("url", Objects.requireNonNull(hardCodedTarget));
            ORIGINAL_MAP.putIfAbsent(feignName + ":url", originalUrl);
            ReflectionUtils.setField("url", hardCodedTarget, newUrl);
            if (resetFlag) {
                LOGGER.info("reset {} success" + feignName);
            } else {
                LOGGER.info("{} url change to {} success", feignName, newUrl);
            }
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
