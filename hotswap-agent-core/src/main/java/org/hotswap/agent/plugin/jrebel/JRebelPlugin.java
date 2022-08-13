package org.hotswap.agent.plugin.jrebel;


import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.util.HashMap;
import java.util.Map;

@Plugin(name = "JRebel",
        description = "redefine class after class change if jrebel exists.",
        testedVersions = {"All"},
        expectedVersions = {"All"})
public class JRebelPlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(JRebelPlugin.class);
    static boolean existJRebel;
    @Init
    PluginManager pluginManager;
    @Init
    Scheduler scheduler;
    Command hotswapCommand;
    final Map<Class<?>, byte[]> reloadMap = new HashMap<>();

    @Init
    public static void init(PluginConfiguration pluginConfiguration, ClassLoader appClassLoader) {
        if (appClassLoader == null) {
            return;
        }
        LOGGER.debug("Init plugin at classLoader {}", appClassLoader);
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (stackTraceElement.getClassName().startsWith("com.zeroturnaround.javarebel")) {
                existJRebel = true;
                break;
            }
        }
        JRebelPlugin plugin = PluginManagerInvoker.callInitializePlugin(JRebelPlugin.class, appClassLoader);
        plugin.initHotswapCommand();
    }

    public void initHotswapCommand() {
        hotswapCommand = () -> pluginManager.hotswap(reloadMap);
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = {FileEvent.MODIFY, FileEvent.CREATE})
    public void registerClassListeners(CtClass ctClass, ClassLoader appClassLoader) {
        if (!existJRebel) {
            return;
        }
        LOGGER.debug("receive ct class change:{}", ctClass.getName());
        Class<?> clazz;
        try {
            clazz = appClassLoader.loadClass(ctClass.getName());
        } catch (ClassNotFoundException e) {
            LOGGER.warning("tries to reload class {}, which is not known to application classLoader {}.",
                    ctClass.getName(), appClassLoader);
            return;
        }

        synchronized (reloadMap) {
            try {
                reloadMap.put(clazz, ctClass.toBytecode());
            } catch (Exception e) {
                LOGGER.error("tries to redefine class {} error.", e);
            }
        }
        scheduler.scheduleCommand(hotswapCommand, 500, Scheduler.DuplicateSheduleBehaviour.SKIP);
    }

}
