package org.hotswap.agent.plugin.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

public class SpringTransformers {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(SpringTransformers.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.context.support.AbstractApplicationContext")
    public static void patchAbstractApplicationContext(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("obtainFreshBeanFactory");
        StringBuilder src = new StringBuilder();
        src.append("{");
        src.append("    org.hotswap.agent.plugin.spring.SpringCorePlugin.setApplicationContext(this);");
        src.append("    org.hotswap.agent.plugin.spring.SpringCorePlugin.setBeanFactory($_);");
        src.append("    return $_;");
        src.append("}");
        method.insertAfter(src.toString());
        LOGGER.info("AbstractApplicationContext patched.");
    }


}
