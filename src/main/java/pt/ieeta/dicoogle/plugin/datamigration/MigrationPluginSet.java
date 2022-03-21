package pt.ieeta.dicoogle.plugin.datamigration;

import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.dcm4che2.net.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ieeta.dicoogle.plugin.datamigration.webserver.DMWebServerPlugin;
import pt.ua.dicoogle.sdk.*;
import pt.ua.dicoogle.sdk.settings.ConfigurationHolder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

@PluginImplementation
public class MigrationPluginSet implements PluginSet {

    private static final Logger logger = LoggerFactory.getLogger(MigrationPluginSet.class);

    private final DMWebServerPlugin webServerPlugin;
    private final DataMigrationPlugin dataMigrationPlugin;

    private ConfigurationHolder settings;

    public MigrationPluginSet() throws InterruptedException, ConfigurationException, IOException {
        logger.info("Initializing My Plugin Set");

        this.dataMigrationPlugin = new DataMigrationPlugin();
        this.webServerPlugin = new DMWebServerPlugin();

        logger.info("My Plugin Set is ready");
    }

    @Override
    public Collection<JettyPluginInterface> getJettyPlugins() {
        return Arrays.asList(this.webServerPlugin);
    }

    @Override
    public void setSettings(ConfigurationHolder configurationHolder) {
        this.settings = configurationHolder;
    }

    @Override
    public ConfigurationHolder getSettings() {
        return this.settings;
    }

    @Override
    public String getName() {
        return "data-migration-plugin";
    }

    @Override
    public void shutdown() {}
}
