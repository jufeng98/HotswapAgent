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
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yudong
 */
public class ReferenceBeanProxy {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ReferenceBeanProxy.class);
    private final ReferenceBean<?> referenceBean;
    private Object ref;
    private Object proxyInstance;
    private static final Map<String, ReferenceBeanProxy> proxied = new HashMap<>();

    public static void refresh(BeanDefinitionRegistry registry) {
        for (Map.Entry<String, ReferenceBeanProxy> entry : proxied.entrySet()) {
            entry.getValue().refreshReferenceBean(registry);
        }
    }

    public static void refreshReferenceBean(CtField referenceField) {
        try {
            ReferenceBeanProxy referenceBeanProxy = proxied.get(referenceField.getType().getName());
            if (referenceBeanProxy == null) {
                return;
            }
            Reference annotation = (Reference) referenceField.getAnnotation(Reference.class);
            referenceBeanProxy.rebuildReferenceBean(annotation.version(), annotation.url());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void refreshReferenceBean(BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(referenceBean.getId())) {
            return;
        }
        BeanDefinition beanDefinition = registry.getBeanDefinition(referenceBean.getId());
        String version = (String) beanDefinition.getPropertyValues().get("version");
        String url = (String) beanDefinition.getPropertyValues().get("url");
        rebuildReferenceBean(version, url);
    }

    public void rebuildReferenceBean(String version, String url) {
        ReferenceBean<?> bean = new ReferenceBean<>();
        bean.setInterface(referenceBean.getInterface());
        bean.setApplication(referenceBean.getApplication());
        bean.setRegistries(referenceBean.getRegistries());
        bean.setClient(referenceBean.getClient());
        bean.setProtocol(referenceBean.getProtocol());
        bean.setVersion(version);
        bean.setCheck(false);
        bean.setUrl(url);
        try {
            ref = bean.get();
            LOGGER.info("refresh dubbo reference {}.", referenceBean.getId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ReferenceBeanProxy getWrapper(ReferenceConfig<?> referenceBean) {
        proxied.put(referenceBean.getId(), new ReferenceBeanProxy((ReferenceBean<?>) referenceBean));
        return proxied.get(referenceBean.getId());
    }

    private ReferenceBeanProxy(ReferenceBean<?> referenceBean) {
        this.referenceBean = referenceBean;
    }

    public Object proxy(Object origRef) {
        this.ref = origRef;
        if (proxyInstance == null) {
            ProxyFactory factory = new ProxyFactory();
            factory.setInterfaces(new Class[]{referenceBean.getInterfaceClass()});

            MethodHandler handler = (self, overridden, forwarder, args) -> overridden.invoke(ref, args);

            try {
                proxyInstance = factory.create(new Class[0], null, handler);
            } catch (Exception e) {
                throw new Error("Unable instantiate ReferenceBean proxy", e);
            }
        }
        return proxyInstance;
    }
}