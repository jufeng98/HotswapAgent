package org.hotswap.agent.plugin.dubbo;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.transformers.DubboTransformers;
import org.hotswap.agent.util.spring.util.ClassUtils;
import org.hotswap.agent.util.spring.util.ReflectionUtils;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    @Init
    Instrumentation instrumentation;
    static Map<String, Object> serviceBeans = new HashMap<>(64);
    static Map<String, String> absolutePaths = new ConcurrentHashMap<>(32);
    static Map<String, Class<?>> clazzes = new ConcurrentHashMap<>(32);
    Command reloadConfigurationCommand =
            new ReflectionCommand(this, DubboRefreshCommands.class.getName(), "reloadConfiguration");

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
        LOGGER.debug("receive class change:{}", clazz.getName());
        clazzes.put(clazz.getName(), clazz);
        refresh();
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = FileEvent.MODIFY)
    public void registerClassListeners(CtClass clazz) {
        LOGGER.debug("receive ct class change:{}", clazz.getName());
        try {
            Class<?> clz = ClassUtils.getClassFromClassloader(clazz.getName(), appClassLoader);
            instrumentation.redefineClasses(new ClassDefinition(clz, clazz.toBytecode()));
        } catch (Exception e) {
            LOGGER.error("error", e);
        }
    }

    @OnResourceFileEvent(path = "/", filter = ".*hotswap-dubbo.properties", events = {FileEvent.MODIFY, FileEvent.CREATE})
    public void registerResourceListeners(URL url) throws URISyntaxException {
        LOGGER.debug("receive properties change:{}", url);
        String absolutePath = Paths.get(url.toURI()).toFile().getAbsolutePath();
        absolutePaths.put(absolutePath, absolutePath);
        refresh();
    }

    private void refresh() {
        scheduler.scheduleCommand(reloadConfigurationCommand, 500);
    }


    public static <T> T getMapFromPlugin(String name) {
        Map<?, ?> val = getStaticFieldValue(DubboPlugin.class.getClassLoader(), DubboPlugin.class.getName(), name);
        if (!val.isEmpty()) {
            return (T) val;
        }
        return getStaticFieldValue(ClassLoader.getSystemClassLoader(), DubboPlugin.class.getName(), name);
    }
}