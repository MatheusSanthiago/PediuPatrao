package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

public class PerfilAtualFilter extends OncePerRequestFilter {
    private final UsuarioDetailsService usuarioDetailsService;

    public PerfilAtualFilter(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication atual = SecurityContextHolder.getContext().getAuthentication();
        if (atual instanceof UsernamePasswordAuthenticationToken && atual.isAuthenticated()) {
            try {
                // alterações de perfil e exclusões também valem para sessões já abertas
                UserDetails usuario = usuarioDetailsService.loadUserByUsername(atual.getName());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
            } catch (UsernameNotFoundException e) {
                SecurityContextHolder.clearContext();
                request.getSession().invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
