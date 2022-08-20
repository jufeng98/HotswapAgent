package org.hotswap.agent.plugin.hotswapper;


import org.hotswap.agent.HotswapAgent;
import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.HotswapTransformer;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * @author yudong
 * @date 2022/8/20
 */
@Plugin(name = "FixRedefinePlugin", description = "fix redefine ability after class change.", testedVersions = {"All"},
        expectedVersions = {"All"})
public class FixRedefinePlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(FixRedefinePlugin.class);
    @Init
    HotswapTransformer hotswapTransformer;
    @Init
    ClassLoader appClassLoader;

    @Init
    public static void init(PluginConfiguration pluginConfiguration, ClassLoader appClassLoader) {
        if (!HotswapAgent.isExists()) {
            return;
        }
        if (appClassLoader == null) {
            return;
        }
        LOGGER.debug("init plugin at classLoader {}", appClassLoader);
        PluginManagerInvoker.callInitializePlugin(FixRedefinePlugin.class, appClassLoader);
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = {FileEvent.MODIFY})
    public void registerClassListeners(CtClass ctClass) {
        try {
            LOGGER.debug("receive ct class change:{}", ctClass.getName());
            Class<?> clz = appClassLoader.loadClass(ctClass.getName());
            hotswapTransformer.transformReal(clz.getClassLoader(), ctClass.getName(), clz, clz.getProtectionDomain(),
                    ctClass.toBytecode());
        } catch (Exception e) {
            LOGGER.error("tries to mock redefine class {} error.", e);
        }
    }

}
