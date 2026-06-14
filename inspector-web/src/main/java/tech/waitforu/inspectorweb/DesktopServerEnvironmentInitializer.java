package tech.waitforu.inspectorweb;

import java.util.Map;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

final class DesktopServerEnvironmentInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String PROPERTY_SOURCE_NAME = "desktopServerProperties";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Map<String, Object> fixedServerProperties = Map.of(
                "server.address", NachiNetInspectorApplication.SERVER_ADDRESS,
                "server.port", NachiNetInspectorApplication.PORT);
        applicationContext.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, fixedServerProperties));
    }
}
