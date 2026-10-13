package com.umc.pediupatrao.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import java.math.BigDecimal;
import com.umc.pediupatrao.entity.ItemPedido;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    // ========================
    // USUÁRIOS
    // ========================
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = (List<Usuario>) usuarioService.listarTodos();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("content", "usuarios/lista :: content");
        log.info("Carregando fragmento: usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = usuarioService.buscarPorId(id).map(u -> {
            model.addAttribute("usuario", u);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        }

        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        usuarioService.salvarUsuario(usuario);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id,
            @ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        usuarioService.atualizarUsuario(id, usuario);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id,
            RedirectAttributes redirectAttributes) {
        usuarioService.deletarUsuario(id);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        return "redirect:/usuarios";
    }

    // ========================
    // PEDIDOS
    // ========================
    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        List<Pedido> pedidos = pedidoService.listarPedidos();
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("content", "pedidos :: content");
        log.info("Carregando fragmento: pedidos :: content");
        return "layout";
    }

    // ========================
    // PRODUTOS
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "produtos/lista :: content");
        log.info("Carregando fragmento: produtos/lista :: content");
        return "layout";
    }

    // ========================
    // CLIENTES
    // ========================
    @GetMapping("/clientes")
    public String clientes(Model model) {
        List<Cliente> clientes = clienteService.listarClientes();
        model.addAttribute("clientes", clientes);
        model.addAttribute("content", "clientes/lista :: content");
        log.info("Carregando fragmento: clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        clienteService.novoCliente(cliente);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = clienteService.buscarPorId(id).map(c -> {
            model.addAttribute("cliente", c);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }

        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id,
            @ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        cliente.setId(id);
        clienteService.salvar(cliente);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    // ========================
    // OUTRAS ROTAS
    // ========================
    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/funcionarios")
    public String funcionarios(Model model) {
        model.addAttribute("tituloOpcao", "Funcionários");
        model.addAttribute("descricaoOpcao", "O cadastro de funcionários ainda não está disponível. Contas de acesso e perfis são administrados em Usuários pelo ADMIN.");
        model.addAttribute("content", "opcao-indisponivel :: content");
        return "layout";
    }

    @GetMapping("/entregadores")
    public String entregadores(Model model) {
        model.addAttribute("tituloOpcao", "Entregadores");
        model.addAttribute("descricaoOpcao", "O cadastro de entregadores ainda não está disponível. O acompanhamento da saída para entrega está disponível em Pedidos.");
        model.addAttribute("content", "opcao-indisponivel :: content");
        return "layout";
    }

    @GetMapping("/horarios")
    public String horarios(Model model) {
        model.addAttribute("tituloOpcao", "Horário de Funcionamento");
        model.addAttribute("descricaoOpcao", "A configuração do horário de funcionamento ainda não está disponível.");
        model.addAttribute("content", "opcao-indisponivel :: content");
        return "layout";
    }

    @GetMapping("/teste")
    public String teste() {
        return "teste";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/pedidos/novo")
    public String novoPedido(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("content", "pedido-form :: content");
        return "layout";
    }

    @PostMapping("/pedidos/salvar")
    public String salvarPedido(@RequestParam String clienteId, @RequestParam List<String> produtoId,
                               @RequestParam List<Integer> quantidade, RedirectAttributes mensagens) {
        if (produtoId.size() != quantidade.size()) {
            throw new IllegalArgumentException("Confira os itens do pedido.");
        }
        Pedido pedido = new Pedido();
        pedido.setClienteId(clienteId);
        for (int i = 0; i < produtoId.size(); i++) {
            ItemPedido item = new ItemPedido();
            item.setProdutoId(produtoId.get(i));
            item.setQuantidade(quantidade.get(i));
            pedido.getItens().add(item);
        }
        pedidoService.criarPedido(pedido);
        mensagens.addFlashAttribute("sucesso", "Pedido registrado!");
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/status")
    public String mudarStatus(@PathVariable String id, @RequestParam String status, RedirectAttributes mensagens) {
        pedidoService.atualizarStatus(id, status);
        mensagens.addFlashAttribute("sucesso", "Status atualizado!");
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String descontar(@PathVariable String id, @RequestParam BigDecimal percentual, RedirectAttributes mensagens) {
        pedidoService.aplicarDesconto(id, percentual);
        mensagens.addFlashAttribute("sucesso", "Desconto atualizado!");
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam String justificativa, RedirectAttributes mensagens) {
        pedidoService.cancelarPedido(id, justificativa);
        mensagens.addFlashAttribute("sucesso", "Pedido cancelado!");
        return "redirect:/pedidos";
    }

    @PostMapping("/clientes/deletar/{id}")
    public String excluirCliente(@PathVariable String id, RedirectAttributes mensagens) {
        clienteService.excluir(id);
        mensagens.addFlashAttribute("sucesso", "Cliente excluído!");
        return "redirect:/clientes";
    }

    @GetMapping("/produtos/novo")
    public String novoProduto(Model model) {
        model.addAttribute("produto", new Produto());
        model.addAttribute("content", "produtos/produto-form :: content");
        return "layout";
    }

    @GetMapping("/produtos/editar/{id}")
    public String editarProduto(@PathVariable String id, Model model) {
        model.addAttribute("produto", produtoService.buscarPorId(id));
        model.addAttribute("content", "produtos/produto-form :: content");
        return "layout";
    }

    @PostMapping("/produtos/salvar")
    public String salvarProduto(@ModelAttribute Produto produto, RedirectAttributes mensagens) {
        produtoService.salvar(produto);
        mensagens.addFlashAttribute("sucesso", "Produto salvo!");
        return "redirect:/produtos";
    }

    @PostMapping("/produtos/deletar/{id}")
    public String excluirProduto(@PathVariable String id, RedirectAttributes mensagens) {
        produtoService.excluir(id);
        mensagens.addFlashAttribute("sucesso", "Produto excluído!");
        return "redirect:/produtos";
    }
}
