package sn.ept.eval.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Configuration de déploiement : origine du front autorisée par CORS_ALLOWED_ORIGINS, documentation désactivée.
 * Base H2 propre à cette classe.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cors;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "cors.allowed-origins=https://eval.ept.sn",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
class CorsSwaggerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CORS : l'origine configurée est acceptée, l'origine de développement ne l'est plus")
    void cors_OrigineConfiguree() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://eval.ept.sn")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://eval.ept.sn"));

        mockMvc.perform(options("/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Documentation désactivée : ni OpenAPI ni Swagger UI ne sont servis")
    void swagger_Desactive() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
