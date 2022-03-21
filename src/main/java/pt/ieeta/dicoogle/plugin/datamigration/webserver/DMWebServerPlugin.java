package pt.ieeta.dicoogle.plugin.datamigration.webserver;

import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.dicoogle.sdk.JettyPluginInterface;
import pt.ua.dicoogle.sdk.core.DicooglePlatformInterface;
import pt.ua.dicoogle.sdk.core.PlatformCommunicatorInterface;
import pt.ua.dicoogle.sdk.settings.ConfigurationHolder;

public class DMWebServerPlugin implements JettyPluginInterface, PlatformCommunicatorInterface {

    private static final Logger logger = LoggerFactory.getLogger(DMWebServerPlugin.class);

    private final DMJettyWebService jettyWebService;
    private DicooglePlatformInterface platform;

    public DMWebServerPlugin() {
        this.jettyWebService = new DMJettyWebService(platform);
    }
    @Override
    public HandlerList getJettyHandlers() {

        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/test");
        handler.addServlet(new ServletHolder(this.jettyWebService), "/find");

        HandlerList l = new HandlerList();
        l.addHandler(handler);

        return l;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public boolean enable() {
        return false;
    }

    @Override
    public boolean disable() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void setSettings(ConfigurationHolder configurationHolder) {

    }

    @Override
    public ConfigurationHolder getSettings() {
        return null;
    }

    @Override
    public void setPlatformProxy(DicooglePlatformInterface dicooglePlatformInterface) {
        this.platform = dicooglePlatformInterface;
    }


}
