package org.hotswap.agent.plugin.dubbo;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.transformers.DubboTransformers;
import org.hotswap.agent.util.spring.util.ReflectionUtils;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.hotswap.agent.util.spring.util.ObjectUtils.getStaticFieldValue;

/**
 * Reload Dubbo configuration after entity change.
 *
 * @author yudong
 */
@Plugin(name = "Dubbo",
        description = "Reload dubbo configuration after configuration change.",
        testedVersions = {"All between 2.6.6"},
        expectedVersions = {"2.6.6"},
        supportClass = {DubboTransformers.class})
public class DubboPlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboPlugin.class);
    @Init
    Scheduler scheduler;
    @Init
    ClassLoader appClassLoader;
    static Map<String, Object> serviceBeans = new HashMap<>(64);

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("Dubbo plugin initialized.");
    }

    public void registerServiceBean(Object serviceBean) {
        String interfaceName = ReflectionUtils.getField("id", serviceBean);
        LOGGER.debug("register serviceBean, id:{}", interfaceName);
        serviceBeans.put(interfaceName, serviceBean);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void registerClassListeners(Class<?> clazz) {
        LOGGER.debug("receive class redefine:{}", clazz.getName());
        ReflectionCommand reflectionCommand = new ReflectionCommand(this, DubboRefreshCommands.class.getName(),
                "reloadAfterClassRedefine", appClassLoader, clazz);
        scheduler.scheduleCommand(reflectionCommand, 0);
    }

    @OnResourceFileEvent(path = "/", filter = ".*hotswap-dubbo.properties", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) throws URISyntaxException {
        LOGGER.debug("receive properties change:{}", url);
        String absolutePath = Paths.get(url.toURI()).toFile().getAbsolutePath();
        ReflectionCommand reflectionCommand = new ReflectionCommand(this, DubboRefreshCommands.class.getName(),
                "reloadPropertiesChange", appClassLoader, absolutePath);
        scheduler.scheduleCommand(reflectionCommand, 0);
    }

    public static <T> T getMapFromPlugin(String name) {
        Map<?, ?> val = getStaticFieldValue(DubboPlugin.class.getClassLoader(), DubboPlugin.class.getName(), name);
        if (!val.isEmpty()) {
            return (T) val;
        }
        return getStaticFieldValue(ClassLoader.getSystemClassLoader(), DubboPlugin.class.getName(), name);
    }
}