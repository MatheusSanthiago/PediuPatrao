package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Funcionario;
import com.umc.pediupatrao.entity.Entregador;
import com.umc.pediupatrao.entity.HorarioFuncionamento;
import com.umc.pediupatrao.service.FuncionarioService;
import com.umc.pediupatrao.service.EntregadorService;
import com.umc.pediupatrao.service.EntregaService;
import com.umc.pediupatrao.service.HorarioFuncionamentoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ConfiguracoesController {
    private final FuncionarioService funcionarios;
    private final EntregadorService entregadores;
    private final HorarioFuncionamentoService horarios;
    private final EntregaService entregas;

    public ConfiguracoesController(FuncionarioService funcionarios, EntregadorService entregadores,
                                   HorarioFuncionamentoService horarios, EntregaService entregas) {
        this.funcionarios = funcionarios;
        this.entregadores = entregadores;
        this.horarios = horarios;
        this.entregas = entregas;
    }

    @GetMapping("/funcionarios")
    public String funcionarios(Model model) {
        model.addAttribute("funcionarios", funcionarios.listar());
        return pagina(model, "funcionarios/lista");
    }

    @GetMapping("/funcionarios/novo")
    public String novoFuncionario(Model model) {
        model.addAttribute("funcionario", new Funcionario());
        return pagina(model, "funcionarios/form");
    }

    @GetMapping("/funcionarios/editar/{id}")
    public String editarFuncionario(@PathVariable String id, Model model) {
        model.addAttribute("funcionario", funcionarios.buscar(id));
        return pagina(model, "funcionarios/form");
    }

    @PostMapping("/funcionarios/salvar")
    public String salvarFuncionario(@ModelAttribute Funcionario funcionario, Model model, RedirectAttributes mensagens) {
        try {
            funcionarios.salvar(funcionario);
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("funcionario", funcionario);
            return pagina(model, "funcionarios/form");
        }
        mensagens.addFlashAttribute("sucesso", "Funcionário salvo!");
        return "redirect:/funcionarios";
    }

    @GetMapping("/entregadores")
    public String entregadores(Model model) {
        model.addAttribute("entregadores", entregadores.listar());
        return pagina(model, "entregadores/lista");
    }

    @GetMapping("/entregadores/novo")
    public String novoEntregador(Model model) {
        model.addAttribute("entregador", new Entregador());
        return pagina(model, "entregadores/form");
    }

    @GetMapping("/entregadores/editar/{id}")
    public String editarEntregador(@PathVariable String id, Model model) {
        model.addAttribute("entregador", entregadores.buscar(id));
        return pagina(model, "entregadores/form");
    }

    @PostMapping("/entregadores/salvar")
    public String salvarEntregador(@ModelAttribute Entregador entregador, Model model, RedirectAttributes mensagens) {
        try {
            entregadores.salvar(entregador);
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("entregador", entregador);
            return pagina(model, "entregadores/form");
        }
        mensagens.addFlashAttribute("sucesso", "Entregador salvo!");
        return "redirect:/entregadores";
    }

    @GetMapping("/horarios")
    public String horarios(Model model) {
        model.addAttribute("horarios", horarios.listar());
        return pagina(model, "horarios");
    }

    @PostMapping("/horarios/salvar")
    public String salvarHorario(@ModelAttribute HorarioFuncionamento horario, RedirectAttributes mensagens) {
        try {
            horarios.salvar(horario);
            mensagens.addFlashAttribute("sucesso", "Horário salvo!");
        } catch (IllegalArgumentException e) {
            mensagens.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/horarios";
    }

    @GetMapping("/entregas")
    public String entregas(Model model) {
        model.addAttribute("pedidos", entregas.listar());
        model.addAttribute("entregadores", entregadores.listar().stream().filter(Entregador::isAtivo).toList());
        return pagina(model, "entregas");
    }

    @PostMapping("/entregas/{id}/despachar")
    public String despachar(@PathVariable String id, @RequestParam String entregadorId, RedirectAttributes mensagens) {
        entregas.despachar(id, entregadorId);
        mensagens.addFlashAttribute("sucesso", "Pedido saiu para entrega!");
        return "redirect:/entregas";
    }

    @PostMapping("/entregas/{id}/finalizar")
    public String finalizar(@PathVariable String id, RedirectAttributes mensagens) {
        entregas.finalizar(id);
        mensagens.addFlashAttribute("sucesso", "Entrega finalizada!");
        return "redirect:/entregas";
    }

    private String pagina(Model model, String nome) {
        model.addAttribute("content", nome + " :: content");
        return "layout";
    }
}