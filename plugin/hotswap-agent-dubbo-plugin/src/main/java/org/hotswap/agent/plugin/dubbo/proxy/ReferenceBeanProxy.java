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
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.hotswap.agent.util.spring.util.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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

    public static void refreshReferenceBean(CtField referenceField, CtClass ctClass) {
        try {
            String id = ctClass.getName() + ":" + referenceField.getName();
            Object dubboProxy = dubboProxied.get(id);
            Object h = ReflectionUtils.getField("h", dubboProxy);
            Object bean = ReflectionUtils.getField("bean", h);
            Object handler = ReflectionUtils.getField("handler", bean);
            ReferenceBeanProxy referenceBeanProxy = ReflectionUtils.getField("outer", handler);
            if (referenceBeanProxy == null) {
                return;
            }
            Reference annotation = (Reference) referenceField.getAnnotation(Reference.class);
            String version = annotation.version();
            String url = annotation.url();
            Integer timeoutInt = annotation.timeout();
            Integer retriesInt = annotation.retries();
            Boolean checkBol = annotation.check();
            referenceBeanProxy.rebuildReferenceBean(
                    StringUtils.defaultString(version, referenceBeanProxy.referenceBean.getVersion()),
                    StringUtils.defaultString(url, referenceBeanProxy.referenceBean.getUrl()),
                    timeoutInt,
                    retriesInt,
                    checkBol
            );
            LOGGER.debug("refresh dubbo referenceField:{}", id);
        } catch (Exception e) {
            LOGGER.error("refresh dubbo referenceField error:{}", e, referenceField.getName());
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
                StringUtils.defaultString(version, referenceBean.getVersion()),
                StringUtils.defaultString(url, referenceBean.getUrl()),
                timeoutInt,
                retriesInt,
                checkBol
        );
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
        LOGGER.info("refresh dubbo reference {}, new version:{}.", referenceBean.getId(), version);
    }

    public static ReferenceBeanProxy getWrapper(ReferenceConfig<?> referenceBean) {
        proxied.put(referenceBean.getId(), new ReferenceBeanProxy((ReferenceBean<?>) referenceBean));
        return proxied.get(referenceBean.getId());
    }

    public static void registerDubboProxy(Object bean,
                                          Object injectedElement,
                                          Object dubboProxyInstance) {
        Field field = ReflectionUtils.getField("field", injectedElement);
        String id = bean.getClass().getName() + ":" + field.getName();
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