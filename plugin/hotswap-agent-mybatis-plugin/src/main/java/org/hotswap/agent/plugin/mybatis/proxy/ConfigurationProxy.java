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
package org.hotswap.agent.plugin.mybatis.proxy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.MyBatisPlugin;
import org.hotswap.agent.plugin.mybatis.transformers.MyBatisTransformers;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.spring.util.ReflectionUtils;

import static org.hotswap.agent.util.spring.util.ClassUtils.getClassFromClassloader;

/**
 * The Class ConfigurationProxy.
 *
 * @author Vladimir Dvorak
 */
public class ConfigurationProxy {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ConfigurationProxy.class);
    private static Map<XMLConfigBuilder, ConfigurationProxy> proxiedConfigurations = new HashMap<>();

    public static ConfigurationProxy getWrapper(XMLConfigBuilder configBuilder) {
        if (!proxiedConfigurations.containsKey(configBuilder)) {
            proxiedConfigurations.put(configBuilder, new ConfigurationProxy(configBuilder));
        }
        return proxiedConfigurations.get(configBuilder);
    }

    public static void refreshProxiedConfigurations() {
        for (ConfigurationProxy wrapper : proxiedConfigurations.values())
            try {
                wrapper.refreshProxiedConfiguration();
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    private ConfigurationProxy(XMLConfigBuilder configBuilder) {
        this.configBuilder = configBuilder;
    }

    public void refreshProxiedConfiguration() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        this.configuration = new Configuration();
        ReflectionHelper.invoke(configBuilder, MyBatisTransformers.REFRESH_METHOD);
        LOGGER.info("refresh XMLConfigBuilder.");

        fillConfigurationWithOriginal();

        refreshMapperFiles();
    }

    private void fillConfigurationWithOriginal() {
        Object environment = ReflectionUtils.getField("environment", origConfiguration);
        ReflectionUtils.setField("environment", configuration, environment);
        Object interceptorChain = ReflectionUtils.getField("interceptorChain", origConfiguration);
        ReflectionUtils.setField("interceptorChain", configuration, interceptorChain);
        Object typeHandlerRegistry = ReflectionUtils.getField("typeHandlerRegistry", origConfiguration);
        ReflectionUtils.setField("typeHandlerRegistry", configuration, typeHandlerRegistry);
        Object typeAliasRegistry = ReflectionUtils.getField("typeAliasRegistry", origConfiguration);
        ReflectionUtils.setField("typeAliasRegistry", configuration, typeAliasRegistry);
    }

    private Set<String> getMapperFiles(ClassLoader classLoader) {
        Class<?> clz = getClassFromClassloader(MyBatisPlugin.class.getName(), classLoader);
        Field mapperMapField = ReflectionUtils.findField(clz, "mapperMap");
        ReflectionUtils.makeAccessible(mapperMapField);
        Map<String, Object> map = ReflectionUtils.getField(mapperMapField, null);
        return map.keySet();
    }

    private void refreshMapperFiles() {
        Set<String> mapperFiles1 = getMapperFiles(this.getClass().getClassLoader());
        Set<String> mapperFiles2 = getMapperFiles(ClassLoader.getSystemClassLoader());
        Set<String> filesSet = new HashSet<>(mapperFiles1);
        filesSet.addAll(mapperFiles2);
        for (String file : filesSet) {
            try {
                XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(Files.newInputStream(Paths.get(file)),
                        configuration, file, configuration.getSqlFragments());
                xmlMapperBuilder.parse();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                ErrorContext.instance().reset();
            }
        }
        LOGGER.info("refresh mapper file finished:{}", filesSet);
    }

    private XMLConfigBuilder configBuilder;
    private Configuration configuration;
    private Configuration origConfiguration;
    private Configuration proxyInstance;

    public Configuration proxy(Configuration origConfiguration) {
        this.configuration = origConfiguration;
        this.origConfiguration = origConfiguration;
        if (proxyInstance == null) {
            ProxyFactory factory = new ProxyFactory();
            factory.setSuperclass(Configuration.class);

            MethodHandler handler = new MethodHandler() {
                @Override
                public Object invoke(Object self, Method overridden, Method forwarder,
                                     Object[] args) throws Throwable {
                    return overridden.invoke(configuration, args);
                }
            };

            try {
                proxyInstance = (Configuration) factory.create(new Class[0], null, handler);
            } catch (Exception e) {
                throw new Error("Unable instantiate Configuration proxy", e);
            }
        }
        return proxyInstance;
    }
}