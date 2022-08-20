package org.hotswap.agent.plugin.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * @author yudong
 * @date 2022/8/20
 */
public class RefreshTransformers {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(RefreshTransformers.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.web.servlet.DispatcherServlet")
    public static void patchAbstractApplicationContext(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("doService");
        StringBuilder src = new StringBuilder();
        src.append("{");
        src.append("    org.hotswap.agent.plugin.refresh.RefreshPlugin.refreshChanges();");
        src.append("}");
        method.insertBefore(src.toString());
        LOGGER.info("DispatcherServlet patched.");
    }

}
