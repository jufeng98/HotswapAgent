package org.hotswap.agent.plugin.feign;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.feign.transformers.FeignTransformers;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

/**
 * Reload Feign configuration after entity change.
 *
 * @author yudong
 */
@Plugin(name = "Feign",
        description = "Reload Feign configuration after configuration change.",
        testedVersions = {"All between 2.1.2"},
        expectedVersions = {"2.1.2"},
        supportClass = {FeignTransformers.class})
public class FeignPlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(FeignPlugin.class);
    @Init
    Scheduler scheduler;
    @Init
    ClassLoader appClassLoader;

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("Feign plugin initialized.");
    }

    @OnResourceFileEvent(path = "/", filter = ".*hotswap-feign.properties", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) throws URISyntaxException {
        LOGGER.debug("receive properties change:{}", url);
        String absolutePath = Paths.get(url.toURI()).toFile().getAbsolutePath();
        ReflectionCommand reflectionCommand = new ReflectionCommand(this, FeignRefreshCommands.class.getName(),
                "reloadPropertiesChange", appClassLoader, absolutePath);
        scheduler.scheduleCommand(reflectionCommand, 0);
    }

}