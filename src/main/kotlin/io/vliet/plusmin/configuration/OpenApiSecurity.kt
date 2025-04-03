package io.vliet.plusmin.configuration

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.stereotype.Component

@Component
class AuthOpenApiCustomizer : OpenApiCustomizer {
    override fun customise(openApi: OpenAPI) {
        val securitySchemeName = "bearerAuth"
        openApi.components.addSecuritySchemes(
            securitySchemeName,
            SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
        )
        openApi.addSecurityItem(SecurityRequirement().addList(securitySchemeName))
    }
}
