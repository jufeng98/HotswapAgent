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
package org.hotswap.agent.plugin.refresh;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.transformers.RefreshTransformers;
import org.hotswap.agent.util.HotswapTransformer;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.watch.Watcher;
import org.hotswap.agent.watch.nio.AbstractNIO2Watcher;
import org.hotswap.agent.watch.nio.EventDispatcher;

import java.lang.reflect.Method;

import static org.hotswap.agent.util.spring.util.ObjectUtils.getStaticFieldValue;

/**
 * @author yudong
 * @date 2022/8/20
 */
@Plugin(name = "RefreshPlugin",
        description = "refresh all classes and resources change",
        testedVersions = {""},
        expectedVersions = {""},
        supportClass = {RefreshTransformers.class}
)
public class RefreshPlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(RefreshPlugin.class);
    static RefreshPlugin refreshPlugin;
    @Init
    HotswapTransformer hotswapTransformer;
    @Init
    Watcher watcher;

    @Init
    public static void init(PluginConfiguration pluginConfiguration, ClassLoader appClassLoader) {
        if (appClassLoader == null || appClassLoader != ClassLoader.getSystemClassLoader()) {
            return;
        }
        LOGGER.debug("init plugin at classLoader {}", appClassLoader);
        refreshPlugin = PluginManagerInvoker.callInitializePlugin(RefreshPlugin.class, appClassLoader);
    }

    public void refresh() {
        hotswapTransformer.transformRedefineClasses();

        EventDispatcher dispatcher = ((AbstractNIO2Watcher) watcher).getDispatcher();
        dispatcher.dispatchEvents();
    }

    public static void refreshChanges() {
        Object refreshPlugin = getStaticFieldValue(ClassLoader.getSystemClassLoader(),
                RefreshPlugin.class.getName(), "refreshPlugin");
        try {
            Method method = refreshPlugin.getClass().getDeclaredMethod("refresh");
            method.setAccessible(true);
            method.invoke(refreshPlugin);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
