package io.vliet.plusmin.configuration

import io.vliet.plusmin.controller.GebruikerController
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain


@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
class SecurityConfig(
    private val gebruikerRepository: GebruikerRepository,
    private val gebruikerService: GebruikerService
) {
    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Bean
    @Throws(Exception::class)
    fun filterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        return httpSecurity
            .authorizeHttpRequests {
                it
                    .requestMatchers("/api/v1/websocket/**").authenticated()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .anyRequest().authenticated()
            }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .oauth2ResourceServer { it.jwt { it.jwtAuthenticationConverter(jwtAuthenticationConverter()) } }
            .build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter {
            val username = it.claims["username"] as String
            val user = gebruikerRepository.findByEmail(username)
                ?: gebruikerService.save(Gebruiker.GebruikerDTO(email = username))
            logger.debug("In SecurityConfig.jwtAuthenticationConverter voor ${user.username} met ${user.authorities}")
            user.authorities
        }
        return converter
    }
}
