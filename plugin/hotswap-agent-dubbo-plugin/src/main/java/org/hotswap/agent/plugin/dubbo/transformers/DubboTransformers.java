package org.hotswap.agent.plugin.dubbo.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.DubboPlugin;
import org.hotswap.agent.plugin.dubbo.proxy.ReferenceBeanProxy;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * @author yudong
 */
public class DubboTransformers {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboTransformers.class);

    @OnClassLoadEvent(classNameRegexp = "com.alibaba.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor")
    public static void patchAnnotationInjected(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtMethod getMethod = ctClass.getDeclaredMethod("doGetInjectedBean");
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(DubboPlugin.class));
        src.append(ReferenceBeanProxy.class.getName()).append(".registerDubboProxy(bean,injectedElement,$_);");
        src.append("return $_;");
        src.append("}");
        getMethod.insertAfter(src.toString());
        LOGGER.info("ReferenceAnnotationBeanPostProcessor patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.alibaba.dubbo.config.ReferenceConfig")
    public static void patchReferenceConfig(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtMethod getMethod = ctClass.getDeclaredMethod("get");
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(DubboPlugin.class));
        src.append("return ").append(ReferenceBeanProxy.class.getName()).append(".getWrapper(this).proxy($_);");
        src.append("}");
        getMethod.insertAfter(src.toString());
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

    @OnClassLoadEvent(classNameRegexp = "com.alibaba.dubbo.config.spring.schema.DubboBeanDefinitionParser")
    public static void patchDubboBeanDefinitionParser(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtClass elementClass = classPool.get("org.w3c.dom.Element");
        CtClass parserContextClass = classPool.get("org.springframework.beans.factory.xml.ParserContext");

        CtMethod parseMethod = ctClass.getDeclaredMethod("parse", new CtClass[]{elementClass, parserContextClass});

        String parseSrc = "String id = element.getAttribute(\"id\");" +
                "        if ((id == null || id.length() == 0) && required) {" +
                "            String generatedBeanName = element.getAttribute(\"name\");" +
                "            if (generatedBeanName == null || generatedBeanName.length() == 0) {" +
                "                if (\"com.alibaba.dubbo.config.ProtocolConfig\".equals(beanClass.getName())) {" +
                "                    generatedBeanName = \"dubbo\";" +
                "                } else {" +
                "                    generatedBeanName = element.getAttribute(\"interface\");" +
                "                }" +
                "            }" +
                "            if (generatedBeanName == null || generatedBeanName.length() == 0) {" +
                "                generatedBeanName = beanClass.getName();" +
                "            }" +
                "            id = generatedBeanName;" +
                "        }" +
                "        if (id != null && id.length() > 0) {" +
                "            if (parserContext.getRegistry().containsBeanDefinition(id)) {" +
                "                parserContext.getRegistry().removeBeanDefinition(id);" +
                "            }" +
                "        }";
        parseMethod.insertBefore(parseSrc);

        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(DubboPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(DubboPlugin.class, "registerBeanDefinition",
                "$_", "java.lang.Object"));
        src.append("}");
        parseMethod.insertAfter(src.toString());

        LOGGER.info("com.alibaba.dubbo.config.spring.schema.DubboBeanDefinitionParser patched.");
    }

}
