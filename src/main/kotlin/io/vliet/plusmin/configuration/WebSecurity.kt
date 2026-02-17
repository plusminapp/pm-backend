package io.vliet.plusmin.configuration

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.repository.GebruikerRepository
import io.vliet.plusmin.service.GebruikerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import java.util.function.Consumer


@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
class SecurityConfig(
    private val gebruikerRepository: GebruikerRepository,
    private val gebruikerService: GebruikerService
) {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml"
                ).permitAll()
                auth.anyRequest().authenticated()
            }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(customJwtDecoder())
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            val subject = jwt.claims["sub"] as String
            val user = gebruikerRepository.findBySubject(subject)
                ?: gebruikerService.save(Gebruiker.GebruikerDTO(subject = subject))
            logger.debug("jwtAuthenticationConverter voor ${user.username} met ${user.authorities}")
            user.authorities
        }
        return converter
    }

    @Bean
    fun customJwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri("https://api.eu.asgardeo.io/t/plusmin/oauth2/jwks")
            .jwtProcessorCustomizer(Consumer { customizer: ConfigurableJWTProcessor<SecurityContext?>? ->
                customizer!!.setJWSTypeVerifier(
                    DefaultJOSEObjectTypeVerifier<SecurityContext?>(JOSEObjectType("at+jwt"), JOSEObjectType("JWT"))
                )
            })
            .build()

        return decoder
    }
}
