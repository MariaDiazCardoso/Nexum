package com.nexum.config;

import com.nexum.filter.JwtAuthFilter;
import com.nexum.security.RestAccessDeniedHandler;
import com.nexum.security.RestAuthenticationEntryPoint;
import com.nexum.service.UsuarioDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtAuthFilter jwtAuthFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authenticationProvider(daoAuthenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        // Preflight CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Auth y registro
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/salud", "/api/certificados/verificar/*").permitAll()

                        // Los archivos de las lecciones siempre requieren una sesion.
                        .requestMatchers(HttpMethod.GET, "/api/lecciones/*/video/**", "/api/lecciones/*/materiales/**").authenticated()

                        // CATÁLOGO PÚBLICO (solo lectura, sin login)
                        .requestMatchers(HttpMethod.GET,
                                "/api/avatares/*",
                                "/api/planes-ia",
                                "/api/categorias",
                                "/api/categorias/**",
                                "/api/cursos",
                                "/api/cursos/**",
                                "/api/modulos",
                                "/api/modulos/**",
                                "/api/lecciones",
                                "/api/lecciones/**"
                        ).permitAll()

                        // Escritura del catálogo: solo ADMINISTRADOR
                        .requestMatchers(HttpMethod.POST, "/api/categorias/**", "/api/cursos/**",
                                "/api/modulos/**", "/api/lecciones/**",
                                "/api/actividades/**", "/api/examenes/**", "/api/preguntas/**")
                                .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT, "/api/categorias/**", "/api/cursos/**",
                                "/api/modulos/**", "/api/lecciones/**",
                                "/api/actividades/**", "/api/examenes/**", "/api/preguntas/**")
                                .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/categorias/**", "/api/cursos/**",
                                "/api/modulos/**", "/api/lecciones/**",
                                "/api/actividades/**", "/api/examenes/**", "/api/preguntas/**")
                                .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/categorias/**", "/api/cursos/**",
                                "/api/modulos/**", "/api/lecciones/**",
                                "/api/actividades/**", "/api/examenes/**", "/api/preguntas/**")
                                .hasRole("ADMINISTRADOR")

                        // Resto de la app: requiere login (inscripciones, progreso, IA, etc.)
                        .requestMatchers("/api/usuarios/**").authenticated()
                        .requestMatchers("/api/inscripciones/**", "/api/progresos-leccion/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(usuarioDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:4200,http://localhost:4306,http://127.0.0.1:4200,http://127.0.0.1:4306}")
            String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        String origins = allowedOrigins == null
                ? "http://localhost:4200,http://localhost:4306,http://127.0.0.1:4200,http://127.0.0.1:4306"
                : allowedOrigins;
        configuration.setAllowedOrigins(Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}