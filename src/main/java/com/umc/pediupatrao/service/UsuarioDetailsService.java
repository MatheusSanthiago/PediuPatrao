package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {
    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));
        String perfil = usuario.getRole();
        // contas antigas sem perfil recebem o menor privilégio
        if (perfil == null || "USER".equals(perfil)) {
            perfil = "ATENDENTE";
        }
        if (!List.of("ADMIN", "GERENTE", "ATENDENTE").contains(perfil)) {
            throw new UsernameNotFoundException("Perfil inválido.");
        }
        return User.withUsername(usuario.getUsername()).password(usuario.getPassword()).roles(perfil).build();
    }
}
