package tech.waitforu.inspectorweb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NachiNetInspectorApplicationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesInspectionHomePage() throws Exception {
        MvcResult homePage = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult indexPage = mockMvc.perform(get("/" + homePage.getResponse().getForwardedUrl()))
                .andExpect(status().isOk())
                .andReturn();

        String content = new String(indexPage.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(content, containsString("Nachi Net Inspector"));
        assertThat(content, containsString("开始解析"));
    }
}
