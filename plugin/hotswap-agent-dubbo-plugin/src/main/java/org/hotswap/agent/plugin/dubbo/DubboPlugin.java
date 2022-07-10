package org.hotswap.agent.plugin.dubbo;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.transformers.DubboTransformers;
import org.hotswap.agent.util.spring.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    static Object beanFactory;
    static Map<String, Object> beanDefinitions = new HashMap<>(64);
    static Map<String, Object> serviceBeans = new HashMap<>(64);
    static Map<String, String> absolutePaths = new ConcurrentHashMap<>(32);
    static Map<String, CtClass> clazzes = new ConcurrentHashMap<>(32);
    Command reloadConfigurationCommand =
            new ReflectionCommand(this, DubboRefreshCommands.class.getName(), "reloadConfiguration");

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("Dubbo plugin initialized.");
    }

    public void registerBeanDefinition(Object beanDefinition) {
        try {
            Object propertyValues = ReflectionUtils.getField("propertyValues", beanDefinition);
            Method getMethod = propertyValues.getClass().getDeclaredMethod("get", String.class);
            getMethod.setAccessible(true);
            String id = (String) getMethod.invoke(propertyValues, "id");
            beanDefinitions.put(id, beanDefinition);
            LOGGER.debug("register beanDefinition, id:{}", id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void registerServiceBean(Object serviceBean) {
        String id = ReflectionUtils.getField("id", serviceBean);
        LOGGER.debug("register serviceBean, id:{}", id);
        serviceBeans.put(id, serviceBean);
    }

    public void registerBeanFactory(Object beanFactory) {
        LOGGER.debug("register beanFactory:{}", beanFactory);
        DubboPlugin.beanFactory = beanFactory;
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = {FileEvent.MODIFY})
    public void registerClassListeners(CtClass clazz) {
        LOGGER.debug("receive class change:{}", clazz.getName());
        clazzes.put(clazz.getName(), clazz);
        refresh();
    }

    @OnResourceFileEvent(path = "/", filter = ".*.xml", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) throws URISyntaxException {
        LOGGER.debug("receive xml change:{}", url);
        String absolutePath = Paths.get(url.toURI()).toFile().getAbsolutePath();
        absolutePaths.put(absolutePath, absolutePath);
        refresh();
    }

    private void refresh() {
        scheduler.scheduleCommand(reloadConfigurationCommand, 500);
    }

}