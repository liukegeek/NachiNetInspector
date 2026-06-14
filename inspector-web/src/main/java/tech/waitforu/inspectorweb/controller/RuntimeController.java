package tech.waitforu.inspectorweb.controller;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {
    private final String appVersion;

    public RuntimeController(Optional<BuildProperties> buildProperties) {
        this.appVersion = buildProperties.map(BuildProperties::getVersion).orElse("");
    }

    @GetMapping("/status")
    public Map<String, String> runtime() {
        return Map.of("appVersion", appVersion);
    }
}
