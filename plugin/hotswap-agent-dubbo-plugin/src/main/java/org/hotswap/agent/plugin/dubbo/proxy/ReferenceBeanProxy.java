/*
 * Copyright 2013-2022 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.dubbo.proxy;

import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.spring.ReferenceBean;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.SpringPlugin;
import org.hotswap.agent.util.spring.util.ClassUtils;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.hotswap.agent.util.spring.util.StringUtils;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ReferenceBeanProxy {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ReferenceBeanProxy.class);
    private ReferenceBean<?> referenceBean;
    private Object ref;
    private Object proxyInstance;
    private static final Map<String, ReferenceBeanProxy> proxied = new HashMap<>();
    private static final Map<String, Object> dubboProxied = new HashMap<>();

    public static void refresh(BeanDefinitionRegistry registry) {
        Map<String, ReferenceBeanProxy> map = new HashMap<>(proxied);
        map.forEach((key, value) -> {
            try {
                value.refreshReferenceBean(registry);
            } catch (Exception e) {
                LOGGER.error("refresh dubbo reference error:{}", e, key);
            }
        });
    }

    public static void refresh(String interfaceName, String id, String url) {
        ReferenceBeanProxy referenceBeanProxy = proxied.get(id);
        if (referenceBeanProxy == null) {
            referenceBeanProxy = proxied.get(interfaceName);
        }
        ReferenceBean<?> original = referenceBeanProxy.referenceBean;
        referenceBeanProxy.rebuildReferenceBean(original.getVersion(), url, original.getTimeout(), original.getRetries(), original.isCheck());
        LOGGER.info("refresh dubbo reference properties xml:{}@{} success", interfaceName, id);
    }

    public static void refreshReferenceBean(CtField referenceField, CtClass ctClass) throws Exception {
        refreshReferenceBean(ctClass.getName(), referenceField.getName(),
                referenceField.getType().getName(), (Reference) referenceField.getAnnotation(Reference.class), "");
    }

    public static void refreshReferenceBean(String clsName, String fieldName, String fieldTypeName,
                                            Reference reference, String newUrl) {
        try {
            String id = clsName + ":" + fieldName;
            Object dubboProxy = dubboProxied.get(id);
            if (dubboProxy == null) {
                new Thread(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(2);
                        createNewReferenceBeanToInject(clsName, fieldName, fieldTypeName, reference);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                return;
            }
            ReferenceBeanProxy referenceBeanProxy = getReferenceBeanProxy(dubboProxy);
            if (referenceBeanProxy == null) {
                LOGGER.warning("referenceBeanProxy id:{} not exists", id);
                return;
            }
            String version = reference.version();
            String url = StringUtils.isEmpty(newUrl) ? reference.url() : newUrl;
            Integer timeoutInt = reference.timeout();
            Integer retriesInt = reference.retries();
            Boolean checkBol = reference.check();
            referenceBeanProxy.rebuildReferenceBean(
                    version,
                    url,
                    timeoutInt,
                    retriesInt,
                    checkBol
            );
            LOGGER.info("refresh dubbo reference field:{}#{} success", clsName, fieldName);
        } catch (Exception e) {
            LOGGER.error("refresh dubbo referenceField error:{}", e, fieldName);
        }
    }

    public static void createNewReferenceBeanToInject(String clsName, String fieldName, String fieldTypeName, Reference reference) throws Exception {
        ReferenceBeanProxy referenceBeanProxy = null;
        for (Object it : dubboProxied.values()) {
            referenceBeanProxy = getReferenceBeanProxy(it);
            break;
        }
        if (referenceBeanProxy == null) {
            LOGGER.info("not exists any referenceBean");
            return;
        }
        ReferenceBean<?> bean = new ReferenceBean<>();
        bean.setInterface(Class.forName(fieldTypeName));
        bean.setApplication(referenceBeanProxy.referenceBean.getApplication());
        bean.setRegistries(referenceBeanProxy.referenceBean.getRegistries());
        bean.setClient(referenceBeanProxy.referenceBean.getClient());
        bean.setProtocol(referenceBeanProxy.referenceBean.getProtocol());
        bean.setTimeout(reference.timeout());
        bean.setVersion(reference.version());
        bean.setRetries(reference.retries());
        bean.setCheck(reference.check());
        bean.setUrl(reference.url());
        Object proxyBean = bean.get();
        ConfigurableListableBeanFactory beanFactory = SpringPlugin.get(ReferenceBeanProxy.class.getClassLoader());
        Class<?> aClass = ClassUtils.getClassFromClassloader(clsName, ReferenceBeanProxy.class.getClassLoader());
        Object target = beanFactory.getBean(aClass);
        if (AopUtils.isAopProxy(target) || AopUtils.isJdkDynamicProxy(target)) {
            target = AopProxyUtils.getSingletonTarget(target);
        }
        ReflectionUtils.setField(fieldName, Objects.requireNonNull(target), proxyBean);
        String id = aClass.getName() + ":" + fieldName;
        dubboProxied.put(id, proxyBean);
        beanFactory.registerSingleton(id, proxyBean);
        LOGGER.info("{} new reference field {} inject:{}", target.getClass().getSimpleName(), fieldName, proxyBean);
    }

    public static ReferenceBeanProxy getReferenceBeanProxy(Object dubboProxy) {
        Object h = ReflectionUtils.getField("h", dubboProxy);
        if (h != null) {
            Object bean = ReflectionUtils.getField("bean", h);
            Object handler = ReflectionUtils.getField("handler", Objects.requireNonNull(bean));
            return ReflectionUtils.getField("outer", Objects.requireNonNull(handler));
        } else {
            Object handler = ReflectionUtils.getField("handler", dubboProxy);
            return ReflectionUtils.getField("outer", Objects.requireNonNull(handler));
        }
    }

    public void refreshReferenceBean(BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(referenceBean.getId())) {
            return;
        }
        BeanDefinition beanDefinition = registry.getBeanDefinition(referenceBean.getId());
        String version = (String) beanDefinition.getPropertyValues().get("version");
        String url = (String) beanDefinition.getPropertyValues().get("url");
        String timeout = (String) beanDefinition.getPropertyValues().get("timeout");
        Integer timeoutInt = timeout != null ? Integer.parseInt(timeout) : 0;
        String retries = (String) beanDefinition.getPropertyValues().get("retries");
        Integer retriesInt = retries != null ? Integer.parseInt(retries) : 2;
        String check = (String) beanDefinition.getPropertyValues().get("check");
        Boolean checkBol = Boolean.parseBoolean(check);
        rebuildReferenceBean(
                version,
                url,
                timeoutInt,
                retriesInt,
                checkBol
        );
        LOGGER.info("refresh dubbo reference xml:{} success", referenceBean.getId());
    }

    public void rebuildReferenceBean(String version, String url, Integer timeout, Integer retriesInt, Boolean checkBol) {
        ReferenceBean<?> bean = new ReferenceBean<>();
        bean.setInterface(referenceBean.getInterface());
        bean.setApplication(referenceBean.getApplication());
        bean.setRegistries(referenceBean.getRegistries());
        bean.setClient(referenceBean.getClient());
        bean.setProtocol(referenceBean.getProtocol());
        bean.setTimeout(timeout);
        bean.setVersion(version);
        bean.setRetries(retriesInt);
        bean.setCheck(checkBol);
        bean.setUrl(url);
        try {
            Method initMethod = ReferenceConfig.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(bean);
            this.ref = ReflectionUtils.getField("ref", bean);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.referenceBean = bean;
        LOGGER.debug("rebuild dubbo reference {}, new version:{}.", referenceBean.getId(), version);
    }

    public static ReferenceBeanProxy getWrapper(ReferenceConfig<?> referenceBean) {
        proxied.put(referenceBean.getId(), new ReferenceBeanProxy((ReferenceBean<?>) referenceBean));
        return proxied.get(referenceBean.getId());
    }

    public static void registerDubboProxy(Object bean,
                                          Object injectedElement,
                                          Object dubboProxyInstance) {
        Field field = ReflectionUtils.getField("field", injectedElement);
        String id = bean.getClass().getName() + ":" + Objects.requireNonNull(field).getName();
        dubboProxied.put(id, dubboProxyInstance);
        LOGGER.debug("register dubbo proxy:{},", id);
    }

    private ReferenceBeanProxy(ReferenceBean<?> referenceBean) {
        this.referenceBean = referenceBean;
    }

    public Object proxy(Object origRef) {
        this.ref = origRef;
        if (proxyInstance == null) {
            ProxyFactory factory = new ProxyFactory();
            factory.setInterfaces(new Class[]{referenceBean.getInterfaceClass()});

            MethodHandler handler = new MethodHandler() {
                final ReferenceBeanProxy outer = ReferenceBeanProxy.this;

                @Override
                public Object invoke(Object self, Method overridden, Method forwarder, Object[] args) throws Throwable {
                    return overridden.invoke(outer.ref, args);
                }
            };

            try {
                proxyInstance = factory.create(new Class[0], null, handler);
            } catch (Exception e) {
                throw new Error("Unable instantiate ReferenceBean proxy", e);
            }
        }
        return proxyInstance;
    }
}