package org.hotswap.agent.plugin.feign.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.feign.FeignPlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

public class FeignTransformers {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(FeignTransformers.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.cloud.openfeign.FeignClientsRegistrar")
    public static void patchFeignClientsRegistrar(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("registerBeanDefinitions");
        method.insertBefore(PluginManagerInvoker.buildInitializePlugin(FeignPlugin.class));
        LOGGER.info("org.springframework.cloud.openfeign.FeignClientsRegistrar patched.");
    }

}
