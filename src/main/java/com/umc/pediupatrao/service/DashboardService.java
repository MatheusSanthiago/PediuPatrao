package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class DashboardService {
    private final PedidoRepository pedidos;
    private final ZoneId fuso = ZoneId.of("America/Sao_Paulo");

    public DashboardService(PedidoRepository pedidos) {
        this.pedidos = pedidos;
    }

    public Map<String, Object> consultar(String dataInicial, String dataFinal, String recurso) {
        LocalDate inicio;
        LocalDate fim;
        if (dataInicial == null && dataFinal == null) {
            fim = LocalDate.now(fuso);
            inicio = fim.withDayOfMonth(1);
        } else {
            try {
                if (dataInicial == null || dataFinal == null
                        || !dataInicial.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                        || !dataFinal.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                    throw new IllegalArgumentException("Informe as duas datas no formato AAAA-MM-DD.");
                }
                inicio = LocalDate.parse(dataInicial);
                fim = LocalDate.parse(dataFinal);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Informe datas válidas no formato AAAA-MM-DD.");
            }
        }
        if (fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fim) >= 366) {
            throw new IllegalArgumentException("O período deve ter de 1 a 366 dias, com a data inicial antes ou igual à final.");
        }

        List<Pedido> encontrados = pedidos.buscarPorPeriodo(
                inicio.atStartOfDay(fuso).toInstant(), fim.plusDays(1).atStartOfDay(fuso).toInstant());
        List<Pedido> vendas = new ArrayList<>();
        int incompletos = 0;
        for (Pedido pedido : encontrados) {
            if ("FINALIZADO".equals(pedido.getStatus())) {
                if (dadosCompletos(pedido)) vendas.add(pedido);
                else incompletos++;
            }
        }

        Object dados;
        switch (recurso) {
            case "resumo": dados = resumo(vendas); break;
            case "vendas": dados = vendasPorDia(vendas, inicio, fim); break;
            case "status": dados = situacao(encontrados); break;
            case "produtos": dados = produtos(vendas); break;
            default: throw new IllegalArgumentException("Consulta do dashboard inválida.");
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("dataInicial", inicio.toString());
        resposta.put("dataFinal", fim.toString());
        resposta.put("pedidosNoPeriodo", encontrados.size());
        resposta.put("pedidosSemData", pedidos.countByDataHoraEntradaIsNull());
        resposta.put("pedidosFinalizadosIncompletos", incompletos);
        resposta.put("dados", dados);
        return resposta;
    }

    private boolean dadosCompletos(Pedido pedido) {
        if (pedido.getValorTotal() == null || pedido.getValorTotal().signum() < 0
                || pedido.getItens() == null || pedido.getItens().isEmpty()) return false;
        for (ItemPedido item : pedido.getItens()) {
            if (item == null || item.getProdutoId() == null || item.getProdutoId().isBlank()
                    || item.getNome() == null || item.getNome().isBlank() || item.getQuantidade() <= 0
                    || item.getPrecoUnitario() == null || item.getPrecoUnitario().signum() <= 0
                    || item.getSubtotal() == null || item.getSubtotal().signum() <= 0) return false;
        }
        return true;
    }

    private Map<String, Object> resumo(List<Pedido> vendas) {
        BigDecimal total = BigDecimal.ZERO;
        long itens = 0;
        for (Pedido pedido : vendas) {
            total = total.add(pedido.getValorTotal());
            for (ItemPedido item : pedido.getItens()) itens += item.getQuantidade();
        }
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("totalVendido", total.setScale(2, RoundingMode.HALF_UP));
        dados.put("pedidosFinalizados", vendas.size());
        dados.put("ticketMedio", vendas.isEmpty() ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(vendas.size()), 2, RoundingMode.HALF_UP));
        dados.put("itensVendidos", itens);
        return dados;
    }

    private List<Map<String, Object>> vendasPorDia(List<Pedido> vendas, LocalDate inicio, LocalDate fim) {
        Map<LocalDate, BigDecimal> valores = new LinkedHashMap<>();
        Map<LocalDate, Integer> quantidades = new LinkedHashMap<>();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            valores.put(dia, BigDecimal.ZERO);
            quantidades.put(dia, 0);
        }
        for (Pedido pedido : vendas) {
            LocalDate dia = pedido.getDataHoraEntrada().atZone(fuso).toLocalDate();
            valores.put(dia, valores.get(dia).add(pedido.getValorTotal()));
            quantidades.put(dia, quantidades.get(dia) + 1);
        }
        List<Map<String, Object>> dias = new ArrayList<>();
        for (LocalDate dia : valores.keySet()) {
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("data", dia.toString());
            dados.put("totalVendido", valores.get(dia).setScale(2, RoundingMode.HALF_UP));
            dados.put("pedidosFinalizados", quantidades.get(dia));
            dias.add(dados);
        }
        return dias;
    }

    private Map<String, Integer> situacao(List<Pedido> encontrados) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (String status : List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA",
                "RETIRADO", "FINALIZADO", "CANCELADO", "OUTROS")) contagem.put(status, 0);
        for (Pedido pedido : encontrados) {
            String status = contagem.containsKey(pedido.getStatus()) ? pedido.getStatus() : "OUTROS";
            contagem.put(status, contagem.get(status) + 1);
        }
        return contagem;
    }

    private List<Map<String, Object>> produtos(List<Pedido> vendas) {
        Map<String, Long> quantidades = new LinkedHashMap<>();
        Map<String, String> nomes = new LinkedHashMap<>();
        // usa o nome registrado na venda mais recente do período
        vendas.sort(Comparator.comparing(Pedido::getDataHoraEntrada).thenComparing(Pedido::getId));
        for (Pedido pedido : vendas) {
            for (ItemPedido item : pedido.getItens()) {
                quantidades.put(item.getProdutoId(), quantidades.getOrDefault(item.getProdutoId(), 0L) + item.getQuantidade());
                nomes.put(item.getProdutoId(), item.getNome());
            }
        }
        List<String> ids = new ArrayList<>(quantidades.keySet());
        ids.sort(Comparator.<String, Long>comparing(quantidades::get).reversed()
                .thenComparing(nomes::get).thenComparing(Comparator.naturalOrder()));
        List<Map<String, Object>> ranking = new ArrayList<>();
        for (String id : ids.subList(0, Math.min(5, ids.size()))) {
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("produtoId", id);
            dados.put("nome", nomes.get(id));
            dados.put("quantidade", quantidades.get(id));
            ranking.add(dados);
        }
        return ranking;
    }
}
