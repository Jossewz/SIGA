package com.siga.siga_iea.config;

import com.siga.siga_iea.auth.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final String rememberMeKey;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          @Value("${app.security.remember-me-key:siga-ieaci-dev-remember-me-key}") String rememberMeKey) {
        this.userDetailsService = userDetailsService;
        this.rememberMeKey = rememberMeKey;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Protección CSRF robusta basada en cookies (compatible con SPA / HTMX / Fetch)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/error")
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // Cabeceras estrictas de seguridad web (Criterios OWASP / SEGI)
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(Customizer.withDefaults())
                        .xssProtection(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=()")
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        // Bloqueo explícito de métodos TRACE y TRACK (mitigación XST)
                        .requestMatchers(HttpMethod.valueOf("TRACE"), "/**").denyAll()
                        .requestMatchers(HttpMethod.valueOf("TRACK"), "/**").denyAll()
                        .requestMatchers("/login", "/logout", "/forgot-password", "/reset-password", "/css/**", "/js/**", "/img/**", "/icons/**", "/fonts/**", "/favicon.ico", "/error", "/storage/public/**").permitAll()
                        .requestMatchers("/configuracion/roles/**").hasRole("ADMIN")
                        .requestMatchers("/usuarios/**").hasAnyRole("ADMIN", "PERSONAL_ADMINISTRATIVO")
                        .requestMatchers(HttpMethod.GET, "/estudiantes").hasAnyRole("ADMIN", "PERSONAL_ADMINISTRATIVO")
                        .requestMatchers(HttpMethod.POST, "/personal", "/personal/**", "/estudiantes/**", "/asistencias/excel/importar").hasAnyRole("ADMIN", "PERSONAL_ADMINISTRATIVO")
                        .requestMatchers(HttpMethod.GET, "/asistencias/excel/exportar").hasAnyRole("ADMIN", "PERSONAL_ADMINISTRATIVO")
                        .requestMatchers(HttpMethod.POST, "/clases/crear", "/clases/guardar", "/clases/eliminar", "/clases/mapear-estudiantes", "/clases/promover-estudiantes", "/clases/horarios/**").hasAnyRole("ADMIN", "PERSONAL_ADMINISTRATIVO")
                        .requestMatchers(HttpMethod.POST, "/clases/evaluaciones/**", "/clases/calificaciones/**", "/clases/asistencias/**", "/mi-jornada/**", "/asistencias/abrir-dia", "/asistencias/sesiones/**").hasAnyRole("ADMIN", "PERSONAL_ADMINISTRATIVO", "DOCENTE")
                        .requestMatchers(HttpMethod.POST, "/certificados/responder", "/reportes/atender").hasAnyRole("ADMIN", "PERSONAL_ADMINISTRATIVO")
                        .requestMatchers("/clases/mapear-estudiantes", "/clases/promover-estudiantes").hasAnyRole("ADMIN", "PERSONAL_ADMINISTRATIVO")
                        .requestMatchers(
                                "/matricula/**",
                                "/personal/**",
                                "/ambiental/**",
                                "/configuracion/**",
                                "/usuarios/**",
                                "/perfil/**",
                                "/estudiantes/**",
                                "/clases/**",
                                "/asistencias/**",
                                "/reportes/**",
                                "/calificaciones/**",
                                "/certificados/**",
                                "/mi-jornada/**",
                                "/api/notificaciones/**",
                                "/chat/**",
                                "/soporte/**",
                                "/"
                        ).authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .rememberMeParameter("remember-me")
                        .tokenValiditySeconds(86400 * 7) // 7 días
                        .userDetailsService(userDetailsService)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "remember-me")
                        .permitAll()
                );

        return http.build();
    }

    /**
     * Fuerza la resolución del CsrfToken diferido antes de que la vista escriba
     * el cuerpo HTML, asegurando que la cookie XSRF-TOKEN se envíe en las cabeceras
     * incluso en páginas extensas (> 8 KB) como el login.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}

