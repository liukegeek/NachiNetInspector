package tech.waitforu.inspectorweb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;

class DesktopServerEnvironmentInitializerTest {
    @Test
    void fixedServerBindingOverridesCommandLineWithoutDiscardingOtherProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource(
                "--server.address=0.0.0.0",
                "--server.port=2027",
                "--spring.profiles.active=review"));
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        new DesktopServerEnvironmentInitializer().initialize(context);

        assertEquals("127.0.0.1", environment.getProperty("server.address"));
        assertEquals("2026", environment.getProperty("server.port"));
        assertEquals("review", environment.getProperty("spring.profiles.active"));
    }
}
