package tech.waitforu.inspectorweb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.Properties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuntimeControllerTest {

    @Test
    void returnsBuildVersionAtMinimalRuntimeRoute() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("version", "1.2.3");
        RuntimeController controller = new RuntimeController(
                Optional.of(new BuildProperties(properties)));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appVersion").value("1.2.3"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void returnsEmptyVersionWhenBuildPropertiesAreMissing() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new RuntimeController(Optional.empty()))
                .build();

        mockMvc.perform(get("/api/runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appVersion").value(""))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
