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
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yudong
 */
public class FeignRefreshCommands {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(FeignRefreshCommands.class);
    private static final Map<String, Object> ORIGINAL_MAP = new ConcurrentHashMap<>();

    public static void reloadPropertiesChange(String absolutePath) {
        try {
            refreshPropertiesChange(absolutePath);
        } catch (Exception e) {
            LOGGER.error("reload error", e);
        }
    }


    public static void refreshPropertiesChange(String absolutePath) throws Exception {
        FileSystemResource resource = new FileSystemResource(absolutePath);
        LOGGER.debug("refresh properties:{}", resource.getPath());
        Properties properties = new Properties();
        try (InputStream inputStream = resource.getInputStream()) {
            properties.load(inputStream);
        }
        properties.forEach((key, value) -> changeFeignServiceUrl(key.toString(), value.toString()));
    }

    public static void changeFeignServiceUrl(String feignName, String newUrl) {
        try {
            ConfigurableListableBeanFactory context = SpringPlugin.getBeanFactory();

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
            String originalUrl = ReflectionUtils.getField("url", hardCodedTarget);
            ORIGINAL_MAP.putIfAbsent(feignName + ":url", originalUrl);
            ReflectionUtils.setField("url", hardCodedTarget, newUrl);
            if (resetFlag) {
                LOGGER.info("reset {} success", feignName);
            } else {
                LOGGER.info("{} url change to {} success", feignName, newUrl);
            }
        } catch (Exception e) {
            LOGGER.error("{} error", feignName, e);
        }
    }

}
