package com.umc.pediupatrao.controller;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@ControllerAdvice
public class ModeloControllerAdvice {
    @ModelAttribute
    public void usuarioLogado(Authentication usuario, Model model, HttpServletRequest request) {
        model.addAttribute("configuracoesAberta", List.of("/configuracoes", "/funcionarios", "/entregadores", "/horarios",
                "/produtos", "/usuarios").stream().anyMatch(request.getRequestURI().substring(request.getContextPath().length())::startsWith));
        if (usuario != null) {
            model.addAttribute("usuarioLogado", usuario.getName());
            model.addAttribute("perfilLogado", usuario.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
            model.addAttribute("admin", usuario.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
            model.addAttribute("gerente", usuario.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE")));
            model.addAttribute("atendente", usuario.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ATENDENTE")));
        }
    }
}
