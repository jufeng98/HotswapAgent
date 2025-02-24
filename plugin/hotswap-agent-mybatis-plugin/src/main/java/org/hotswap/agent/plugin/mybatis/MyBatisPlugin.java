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
package org.hotswap.agent.plugin.mybatis;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.transformers.MyBatisTransformers;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.hotswap.agent.util.spring.util.ObjectUtils.getStaticFieldValue;

/**
 * Reload MyBatis configuration after entity create/change.
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "MyBatis",
        description = "Reload MyBatis configuration after configuration create/change.",
        testedVersions = {"All between 3.5.9"},
        expectedVersions = {"3.5.9"},
        supportClass = {MyBatisTransformers.class})
public class MyBatisPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(MyBatisPlugin.class);

    @Init
    Scheduler scheduler;

    static Map<String, Object> mapperMap = new HashMap<>();
    static Map<String, Object> configMap = new HashMap<>();

    Command reloadConfigurationCommand =
            new ReflectionCommand(this, MyBatisRefreshCommands.class.getName(), "reloadConfiguration");

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("MyBatis plugin initialized.");
    }

    public void registerConfigFile(String configFile, Object xmlConfigBuilder) {
        if (configFile != null && !configMap.containsKey(configFile)) {
            LOGGER.info("MyBatisPlugin - config file registered : {}", configFile);
            configMap.put(configFile, xmlConfigBuilder);
        }
    }

    public void registerMapperFile(String mapperFile, Object xmlMapperBuilder) {
        if (mapperFile != null && !mapperMap.containsKey(mapperFile)) {
            LOGGER.debug("MyBatisPlugin - mapper file registered : {}", mapperFile);
            mapperMap.put(mapperFile, xmlMapperBuilder);
        }
    }

    @OnResourceFileEvent(path = "/", filter = ".*.xml", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) throws URISyntaxException {
        String absolutePath = Paths.get(url.toURI()).toFile().getAbsolutePath();
        if (mapperMap.containsKey(absolutePath) || configMap.containsKey(absolutePath)) {
            LOGGER.debug("MyBatisPlugin - registerResourceListeners : {}", url);
            scheduler.scheduleCommand(reloadConfigurationCommand, 0);
        }
    }

    public static <T> T getMapFromPlugin(String name) {
        Map<?, ?> val = getStaticFieldValue(MyBatisPlugin.class.getClassLoader(), MyBatisPlugin.class.getName(), name);
        if (!val.isEmpty()) {
            return (T) val;
        }
        return getStaticFieldValue(ClassLoader.getSystemClassLoader(), MyBatisPlugin.class.getName(), name);
    }
}
