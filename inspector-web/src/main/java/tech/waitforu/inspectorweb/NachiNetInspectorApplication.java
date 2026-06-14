package tech.waitforu.inspectorweb;

import java.io.IOException;
import java.net.BindException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NachiNetInspectorApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(NachiNetInspectorApplication.class);
    private static final int PORT = 2026;
    private static final String URL = "http://localhost:2026";

    public static void main(String[] args) {
        try {
            SpringApplication.run(NachiNetInspectorApplication.class, args);
            LOGGER.info("Nachi Net Inspector started at {}", URL);
            openBrowser(URL);
        } catch (RuntimeException exception) {
            if (isPortInUseException(exception)) {
                LOGGER.info("Port {} is already in use; opening the existing instance at {}", PORT, URL);
                openBrowser(URL);
                return;
            }
            throw exception;
        }
    }

    private static boolean isPortInUseException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BindException
                    || current.getClass().getName().contains("PortInUseException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void openBrowser(String url) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        try {
            if (osName.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            } else if (osName.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (IOException exception) {
            LOGGER.warn("Unable to open browser at {}", url, exception);
        }
    }
}
