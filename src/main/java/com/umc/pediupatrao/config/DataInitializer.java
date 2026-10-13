package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder,
                                  @Value("${app.admin.username:admin}") String username,
                                  @Value("${app.admin.password:}") String password) {
        return args -> {
            if (repo.count() == 0) {
                if (password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                    throw new IllegalStateException("Defina APP_ADMIN_PASSWORD com 8 caracteres ou mais (máximo 72 bytes) para criar o primeiro administrador.");
                }
                Usuario usuario = new Usuario();
                usuario.setUsername(username);
                usuario.setPassword(encoder.encode(password));
                usuario.setRole("ADMIN");
                repo.save(usuario);
            }
        };
    }
}
