package org.hotswap.agent.plugin.dubbo.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.DubboPlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * @author yudong
 */
public class DubboTransformers {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboTransformers.class);

    @OnClassLoadEvent(classNameRegexp = "com.alibaba.dubbo.config.ReferenceConfig")
    public static void patchReferenceConfig(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("get");
        method.insertBefore(PluginManagerInvoker.buildInitializePlugin(DubboPlugin.class));
        LOGGER.info("com.alibaba.dubbo.config.ReferenceConfig patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.alibaba.dubbo.config.spring.ServiceBean")
    public static void patchServiceBean(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("export");
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(DubboPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(DubboPlugin.class, "registerServiceBean",
                "this", "java.lang.Object"));
        src.append("}");
        method.insertBefore(src.toString());
        LOGGER.info("com.alibaba.dubbo.config.spring.ServiceBean patched.");
    }

}
