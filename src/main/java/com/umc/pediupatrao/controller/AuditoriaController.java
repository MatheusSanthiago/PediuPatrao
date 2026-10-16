package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuditoriaController {
    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String pagina(@RequestParam(required = false) String entidade,
                         @RequestParam(required = false) String entidadeId,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("registros", auditoriaService.listar(entidade, entidadeId, pagina));
        model.addAttribute("entidade", entidade);
        model.addAttribute("entidadeId", entidadeId);
        model.addAttribute("content", "auditoria :: content");
        return "layout";
    }

    @ResponseBody
    @GetMapping("/api/auditoria")
    public Page<Auditoria> listar(@RequestParam(required = false) String entidade,
                                 @RequestParam(required = false) String entidadeId,
                                 @RequestParam(defaultValue = "0") int pagina) {
        return auditoriaService.listar(entidade, entidadeId, pagina);
    }
}
