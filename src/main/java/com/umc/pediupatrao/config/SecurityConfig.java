package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioDetailsService usuarioDetailsService) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/login", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/usuarios", "/usuarios/**", "/api/usuarios", "/api/usuarios/**", "/admin").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/auditoria", "/api/auditoria").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers("/api/auditoria/**", "/auditoria/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes", "/api/clientes/*").hasAnyRole("GERENTE", "ATENDENTE")
                .requestMatchers("/clientes/**", "/api/clientes/**").hasRole("ATENDENTE")
                .requestMatchers(HttpMethod.GET, "/pedidos", "/api/pedidos", "/produtos", "/api/produtos").hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                .requestMatchers(HttpMethod.POST, "/api/pedidos", "/pedidos/salvar").hasAnyRole("GERENTE", "ATENDENTE")
                .requestMatchers(HttpMethod.GET, "/pedidos/novo").hasAnyRole("GERENTE", "ATENDENTE")
                .requestMatchers("/pedidos/**", "/api/pedidos/**").hasRole("GERENTE")
                .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers("/", "/api/csrf").authenticated()
                .requestMatchers(HttpMethod.GET, "/configuracoes", "/funcionarios", "/entregadores", "/horarios").authenticated()
                .anyRequest().denyAll())
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
            .exceptionHandling(erros -> erros
                .authenticationEntryPoint((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.sendError(401, "Autenticação necessária.");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
                .accessDeniedHandler((request, response, exception) -> response.sendError(403, "Acesso negado.")))
            .addFilterBefore(new PerfilAtualFilter(usuarioDetailsService), AuthorizationFilter.class);
        // mantém o CSRF ativo em formulários e APIs autenticadas por sessão
        return http.build();
    }
}
