package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    public void salvarUsuario(Usuario usuario) {
        validar(usuario, null);
        validarSenha(usuario.getPassword());
        usuario.setId(null);
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuarioRepository.save(usuario);
    }

    public void deletarUsuario(String id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        if (usuario.getUsername().equals(SecurityContextHolder.getContext().getAuthentication().getName())) {
            throw new IllegalArgumentException("Você não pode excluir a própria conta.");
        }
        usuarioRepository.deleteById(id);
    }

    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public void atualizarUsuario(String id, Usuario dados) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        validar(dados, id);
        if (usuario.getUsername().equals(SecurityContextHolder.getContext().getAuthentication().getName())
                && (!"ADMIN".equals(dados.getRole()) || !usuario.getUsername().equals(dados.getUsername()))) {
            throw new IllegalArgumentException("Você não pode alterar o próprio login ou retirar seu perfil ADMIN.");
        }
        usuario.setUsername(dados.getUsername());
        usuario.setRole(dados.getRole());
        if (dados.getPassword() != null && !dados.getPassword().isBlank()) {
            validarSenha(dados.getPassword());
            usuario.setPassword(passwordEncoder.encode(dados.getPassword()));
        }
        usuarioRepository.save(usuario);
    }

    private void validar(Usuario usuario, String id) {
        if (usuario.getUsername() == null || !usuario.getUsername().matches("[a-zA-Z0-9._-]{3,50}")) {
            throw new IllegalArgumentException("O login deve ter 3 a 50 letras, números, pontos, traços ou sublinhados.");
        }
        if (usuario.getRole() == null || !List.of("ADMIN", "GERENTE", "ATENDENTE").contains(usuario.getRole())) {
            throw new IllegalArgumentException("Selecione ADMIN, GERENTE ou ATENDENTE.");
        }
        Optional<Usuario> existente = usuarioRepository.findByUsername(usuario.getUsername());
        if (existente.isPresent() && !existente.get().getId().equals(id)) {
            throw new IllegalArgumentException("Este login já está cadastrado.");
        }
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < 8 || senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres e no máximo 72 bytes.");
        }
    }
}
