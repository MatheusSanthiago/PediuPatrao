package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Entregador;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.EntregadorRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntregaService {
    private final PedidoRepository pedidos;
    private final EntregadorRepository entregadores;
    private final PedidoService pedidoService;
    private final AuditoriaService auditoria;

    public EntregaService(PedidoRepository pedidos, EntregadorRepository entregadores,
                         PedidoService pedidoService, AuditoriaService auditoria) {
        this.pedidos = pedidos;
        this.entregadores = entregadores;
        this.pedidoService = pedidoService;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public List<Pedido> listar() {
        return pedidos.findAll().stream().filter(p ->
                "PRONTO".equals(p.getStatus()) || "SAIU_PARA_ENTREGA".equals(p.getStatus())
                || ("FINALIZADO".equals(p.getStatus()) && p.getEntregadorId() != null)).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('GERENTE')")
    public void despachar(String pedidoId, String entregadorId) {
        Pedido pedido = pedidos.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (!"PRONTO".equals(pedido.getStatus())) {
            throw new IllegalArgumentException("Somente pedidos prontos podem sair para entrega.");
        }
        if (entregadorId == null || entregadorId.isBlank()) {
            throw new IllegalArgumentException("Selecione um entregador.");
        }
        Entregador entregador = entregadores.findById(entregadorId)
                .orElseThrow(() -> new IllegalArgumentException("Entregador não encontrado."));
        if (!entregador.isAtivo()) throw new IllegalArgumentException("Selecione um entregador ativo.");
        Map<String, String> antes = new LinkedHashMap<>();
        antes.put("entregadorId", pedido.getEntregadorId());
        antes.put("entregadorNome", pedido.getEntregadorNome());
        pedido.setEntregadorId(entregador.getId());
        pedido.setEntregadorNome(entregador.getNome());
        pedidos.save(pedido);
        auditoria.registrar("ATRIBUICAO_ENTREGADOR", "PEDIDO", pedidoId, antes,
                Map.of("entregadorId", entregador.getId(), "entregadorNome", entregador.getNome()), null);
        pedidoService.atualizarStatus(pedidoId, "SAIU_PARA_ENTREGA");
    }

    @Transactional
    @PreAuthorize("hasRole('GERENTE')")
    public void finalizar(String pedidoId) {
        Pedido pedido = pedidos.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (!"SAIU_PARA_ENTREGA".equals(pedido.getStatus())) {
            throw new IllegalArgumentException("Somente entregas em andamento podem ser finalizadas.");
        }
        pedidoService.atualizarStatus(pedidoId, "FINALIZADO");
    }
}