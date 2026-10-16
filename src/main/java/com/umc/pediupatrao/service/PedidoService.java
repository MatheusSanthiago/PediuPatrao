package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {
    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Pedido criarPedido(Pedido dados) {
        if (dados.getClienteId() == null || !clienteRepository.existsById(dados.getClienteId())) {
            throw new IllegalArgumentException("Selecione um cliente cadastrado.");
        }
        if (dados.getItens() == null || dados.getItens().isEmpty() || dados.getItens().size() > 50) {
            throw new IllegalArgumentException("Informe de 1 a 50 itens no pedido.");
        }

        // usa somente produto e quantidade enviados pelo formulário
        Pedido pedido = new Pedido();
        pedido.setClienteId(dados.getClienteId());
        BigDecimal subtotal = BigDecimal.ZERO;
        int quantidade = 0;
        for (ItemPedido dadosItem : dados.getItens()) {
            if (dadosItem == null || dadosItem.getProdutoId() == null
                    || dadosItem.getQuantidade() < 1 || dadosItem.getQuantidade() > 100) {
                throw new IllegalArgumentException("Cada item deve ter um produto e quantidade entre 1 e 100.");
            }
            Produto produto = produtoRepository.findById(dadosItem.getProdutoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (!Boolean.TRUE.equals(produto.getAtivo()) || produto.getPreco() == null
                    || !Double.isFinite(produto.getPreco()) || produto.getPreco() < 0.01
                    || BigDecimal.valueOf(produto.getPreco()).stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("O produto precisa estar ativo e ter um preço válido.");
            }
            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNome(produto.getNome());
            item.setQuantidade(dadosItem.getQuantidade());
            item.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP));
            item.setSubtotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
            pedido.getItens().add(item);
            subtotal = subtotal.add(item.getSubtotal());
            quantidade += item.getQuantidade();
        }
        pedido.setQuantidade(quantidade);
        pedido.setValorSubtotal(subtotal);
        pedido.setValorTotal(subtotal);
        pedido.setStatus("RECEBIDO");
        pedido.setDataHoraEntrada(Instant.now());
        pedido.setResponsavelRegistro(usuarioAtual());
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("INCLUSAO_PEDIDO", "PEDIDO", salvo.getId(), Map.of(), dadosAuditoria(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    @Transactional
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido atualizarStatus(String id, String status) {
        Pedido pedido = buscar(id);
        boolean permitido = ("RECEBIDO".equals(pedido.getStatus()) && "EM_PREPARACAO".equals(status))
                || ("EM_PREPARACAO".equals(pedido.getStatus()) && "PRONTO".equals(status))
                || ("PRONTO".equals(pedido.getStatus()) && ("SAIU_PARA_ENTREGA".equals(status) || "RETIRADO".equals(status)))
                || (("SAIU_PARA_ENTREGA".equals(pedido.getStatus()) || "RETIRADO".equals(pedido.getStatus()))
                    && "FINALIZADO".equals(status));
        if (!permitido) {
            throw new IllegalArgumentException("Transição de status não permitida.");
        }
        Map<String, String> antes = dadosAuditoria(pedido);
        pedido.setStatus(status);
        if ("SAIU_PARA_ENTREGA".equals(status) || "RETIRADO".equals(status)) {
            pedido.setDataHoraSaida(Instant.now());
            pedido.setResponsavelSaida(usuarioAtual());
        }
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("ALTERACAO_STATUS", "PEDIDO", id, antes, dadosAuditoria(salvo), null);
        return salvo;
    }

    @Transactional
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) < 0
                || percentual.compareTo(new BigDecimal("20")) > 0 || percentual.scale() > 2) {
            throw new IllegalArgumentException("Informe um desconto de 0 a 20%, com até duas casas decimais.");
        }
        Pedido pedido = buscar(id);
        validarAntesDaSaida(pedido);
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new IllegalArgumentException("Pedido antigo sem itens e preços. Não é possível aplicar desconto.");
        }
        Map<String, String> antes = dadosAuditoria(pedido);
        pedido.setDescontoPercentual(percentual);
        pedido.setValorDesconto(pedido.getValorSubtotal().multiply(percentual)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        pedido.setValorTotal(pedido.getValorSubtotal().subtract(pedido.getValorDesconto()));
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("DESCONTO_PEDIDO", "PEDIDO", id, antes, dadosAuditoria(salvo), null);
        return salvo;
    }

    @Transactional
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelarPedido(String id, String justificativa) {
        if (justificativa == null || justificativa.isBlank() || justificativa.trim().length() > 500) {
            throw new IllegalArgumentException("Informe a justificativa do cancelamento, com até 500 caracteres.");
        }
        Pedido pedido = buscar(id);
        validarAntesDaSaida(pedido);
        Map<String, String> antes = dadosAuditoria(pedido);
        pedido.setStatus("CANCELADO");
        pedido.setJustificativaCancelamento(justificativa.trim());
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("CANCELAMENTO_PEDIDO", "PEDIDO", id, antes, dadosAuditoria(salvo), justificativa.trim());
        return salvo;
    }

    private Pedido buscar(String id) {
        return pedidoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
    }

    private void validarAntesDaSaida(Pedido pedido) {
        if (pedido.getDataHoraSaida() != null || pedido.getStatus() == null
                || !List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO").contains(pedido.getStatus())) {
            throw new IllegalArgumentException("A operação só é permitida antes da saída, em pedidos não cancelados.");
        }
    }

    private String usuarioAtual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Map<String, String> dadosAuditoria(Pedido pedido) {
        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("clienteId", pedido.getClienteId());
        dados.put("status", pedido.getStatus());
        dados.put("quantidade", String.valueOf(pedido.getQuantidade()));
        dados.put("valorSubtotal", String.valueOf(pedido.getValorSubtotal()));
        dados.put("descontoPercentual", String.valueOf(pedido.getDescontoPercentual()));
        dados.put("valorDesconto", String.valueOf(pedido.getValorDesconto()));
        dados.put("valorTotal", String.valueOf(pedido.getValorTotal()));
        dados.put("responsavelRegistro", pedido.getResponsavelRegistro());
        dados.put("dataHoraEntrada", pedido.getDataHoraEntrada() == null ? null : pedido.getDataHoraEntrada().toString());
        dados.put("responsavelSaida", pedido.getResponsavelSaida());
        dados.put("dataHoraSaida", pedido.getDataHoraSaida() == null ? null : pedido.getDataHoraSaida().toString());
        dados.put("justificativaCancelamento", pedido.getJustificativaCancelamento());
        for (int i = 0; i < pedido.getItens().size(); i++) {
            ItemPedido item = pedido.getItens().get(i);
            String campo = "itens[" + i + "].";
            dados.put(campo + "produtoId", item.getProdutoId());
            dados.put(campo + "nome", item.getNome());
            dados.put(campo + "quantidade", String.valueOf(item.getQuantidade()));
            dados.put(campo + "precoUnitario", String.valueOf(item.getPrecoUnitario()));
            dados.put(campo + "subtotal", String.valueOf(item.getSubtotal()));
        }
        return dados;
    }
}
