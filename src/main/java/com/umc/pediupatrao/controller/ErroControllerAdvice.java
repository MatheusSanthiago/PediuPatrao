package com.umc.pediupatrao.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class ErroControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    public Object dadosInvalidos(IllegalArgumentException erro, HttpServletRequest request, HttpServletResponse response) {
        return responder(400, erro.getMessage(), request, response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object acessoNegado(AccessDeniedException erro, HttpServletRequest request, HttpServletResponse response) {
        return responder(403, "Você não tem permissão para esta operação.", request, response);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Object conflito(OptimisticLockingFailureException erro, HttpServletRequest request, HttpServletResponse response) {
        return responder(409, "O registro foi alterado por outra pessoa. Atualize a página e tente novamente.", request, response);
    }

    @ExceptionHandler(DataAccessException.class)
    public Object bancoIndisponivel(DataAccessException erro, HttpServletRequest request, HttpServletResponse response) {
        return responder(503, "Não foi possível concluir a operação no banco. Verifique a conexão e a configuração do replica set.", request, response);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Object registroDuplicado(DuplicateKeyException erro, HttpServletRequest request, HttpServletResponse response) {
        return responder(409, "Já existe um registro com este identificador ou login.", request, response);
    }

    private Object responder(int status, String mensagem, HttpServletRequest request, HttpServletResponse response) {
        if (request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity.status(status).body(Map.of("status", status, "mensagem", mensagem));
        }
        response.setStatus(status);
        ModelAndView pagina = new ModelAndView("error");
        pagina.addObject("status", status);
        pagina.addObject("message", mensagem);
        return pagina;
    }
}
