package sn.ept.eval.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        Schema<?> problemDetailSchema = new ObjectSchema()
                .description("Format international d'erreur d'API RFC 7807 (Problem Details for HTTP APIs)")
                .addProperty("type", new StringSchema().example("https://eval.ept.sn/errors/temps-expire"))
                .addProperty("title", new StringSchema().example("Temps d'examen dépassé"))
                .addProperty("status", new IntegerSchema().example(409))
                .addProperty("detail", new StringSchema().example("Le temps imparti au-delà de la marge de tolérance est écoulé. La copie a été figée."))
                .addProperty("instance", new StringSchema().example("/api/soumissions/100/soumettre"))
                .addProperty("code", new StringSchema().example("TEMPS_EXPIRE"))
                .addProperty("timestamp", new DateTimeSchema().example("2026-10-18T12:00:00"));

        return new OpenAPI()
                .info(new Info()
                        .title("Plateforme d'Évaluation EPT — API Documentation")
                        .version("1.0.0")
                        .description("Documentation interactive OpenAPI/Swagger des endpoints REST du projet transversal DIC1-GIT (Norme RFC 7807, Anti-Triche et Passation).")
                        .contact(new Contact().name("Équipe DIC1 EPT").email("mor.leye@ept.sn")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Entrez le token JWT obtenu via POST /api/auth/login"))
                        .addSchemas("ProblemDetail", problemDetailSchema));
    }
}
