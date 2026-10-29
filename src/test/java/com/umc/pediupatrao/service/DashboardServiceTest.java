package com.umc.pediupatrao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    private PedidoRepository pedidos;
    private DashboardService service;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void preparar() {
        pedidos = mock(PedidoRepository.class);
        service = new DashboardService(pedidos);
        when(pedidos.buscarPorPeriodo(any(), any())).thenReturn(List.of());
    }

    @Test
    void somaValoresComDescontoESomentePedidosFinalizados() {
        Pedido primeiro = pedido("1", "FINALIZADO", "48.08", 2);
        Pedido segundo = pedido("2", "FINALIZADO", "30.05", 1);
        carregar(List.of(primeiro, segundo, pedido("3", "CANCELADO", "100", 5), pedido("4", "PRONTO", "50", 2)));
        JsonNode resposta = consultar("resumo");
        assertEquals(4, resposta.get("pedidosNoPeriodo").asInt());
        JsonNode dados = resposta.get("dados");
        assertEquals(new BigDecimal("78.13"), dados.get("totalVendido").decimalValue());
        assertEquals(new BigDecimal("39.07"), dados.get("ticketMedio").decimalValue());
        assertEquals(2, dados.get("pedidosFinalizados").asInt());
        assertEquals(3, dados.get("itensVendidos").asInt());
    }

    @Test
    void usaDiaDeBrasiliaEPreencheDiasSemVendasComZero() {
        Pedido pedido = pedido("1", "FINALIZADO", "30.05", 1);
        pedido.setDataHoraEntrada(Instant.parse("2026-10-02T02:59:59Z"));
        carregar(List.of(pedido));
        JsonNode dados = consultar("vendas").get("dados");
        assertEquals(3, dados.size());
        assertEquals("2026-10-01", dados.get(0).get("data").asText());
        assertEquals(30.05, dados.get(0).get("totalVendido").asDouble());
        assertEquals(1, dados.get(0).get("pedidosFinalizados").asInt());
        assertEquals(0, dados.get(1).get("totalVendido").asInt());
        verify(pedidos).buscarPorPeriodo(
                Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-10-04T03:00:00Z"));
    }

    @Test
    void contaTodosOsStatusInclusiveCanceladoEDesconhecido() {
        carregar(List.of(pedido("1", "FINALIZADO", "30", 1), pedido("2", "CANCELADO", "30", 1),
                pedido("3", "PRONTO", "30", 1), pedido("4", null, "30", 1)));
        JsonNode dados = consultar("status").get("dados");
        assertEquals(1, dados.get("FINALIZADO").asInt());
        assertEquals(1, dados.get("CANCELADO").asInt());
        assertEquals(1, dados.get("PRONTO").asInt());
        assertEquals(1, dados.get("OUTROS").asInt());
        assertEquals(0, dados.get("RECEBIDO").asInt());
    }

    @Test
    void rankingAgrupaPorIdLimitaCincoEDesempataPeloNome() {
        List<Pedido> lista = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Pedido pedido = pedido("pedido-" + i, "FINALIZADO", "30.05", 1);
            pedido.getItens().getFirst().setProdutoId("produto-" + i);
            pedido.getItens().getFirst().setNome("Produto " + i);
            lista.add(pedido);
        }
        Pedido repetido = pedido("repetido", "FINALIZADO", "60.10", 2);
        repetido.getItens().getFirst().setProdutoId("produto-6");
        repetido.getItens().getFirst().setNome("Nome atualizado");
        repetido.setDataHoraEntrada(Instant.parse("2026-10-02T15:00:00Z"));
        lista.add(repetido);
        carregar(lista);
        JsonNode dados = consultar("produtos").get("dados");
        assertEquals(5, dados.size());
        assertEquals("produto-6", dados.get(0).get("produtoId").asText());
        assertEquals("Nome atualizado", dados.get(0).get("nome").asText());
        assertEquals(3, dados.get(0).get("quantidade").asInt());
        assertEquals("produto-0", dados.get(1).get("produtoId").asText());
    }

    @Test
    void avisaDadosIncompletosSemInventarVendas() {
        Pedido semItens = pedido("1", "FINALIZADO", "30", 1);
        semItens.setItens(List.of());
        Pedido semTotal = pedido("2", "FINALIZADO", "30", 1);
        semTotal.setValorTotal(null);
        Pedido itemInvalido = pedido("3", "FINALIZADO", "30", 1);
        itemInvalido.getItens().getFirst().setQuantidade(0);
        carregar(List.of(semItens, semTotal, itemInvalido));
        when(pedidos.countByDataHoraEntradaIsNull()).thenReturn(2L);
        JsonNode resposta = consultar("resumo");
        assertEquals(3, resposta.get("pedidosFinalizadosIncompletos").asInt());
        assertEquals(2, resposta.get("pedidosSemData").asInt());
        assertEquals(0, resposta.get("dados").get("totalVendido").asInt());
        assertEquals(0, resposta.get("dados").get("pedidosFinalizados").asInt());
        assertEquals(0, consultar("produtos").get("dados").size());
        assertEquals(3, consultar("status").get("dados").get("FINALIZADO").asInt());
    }

    @ParameterizedTest
    @CsvSource(value = {"2026-10-03,2026-10-01", "2026-02-30,2026-03-01", "01/10/2026,2026-10-03",
            "2026-1-01,2026-10-03", "2025-01-01,2026-01-02", "NULL,2026-10-03", "2026-10-01,NULL", "'',2026-10-03"}, nullValues = "NULL")
    void rejeitaPeriodoInvalidoAntesDeConsultarBanco(String inicio, String fim) {
        assertThrows(IllegalArgumentException.class, () -> service.consultar(inicio, fim, "resumo"));
        verifyNoInteractions(pedidos);
    }

    @Test
    void periodoPadraoVaiDoInicioDoMesAteHoje() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        JsonNode resposta = json.valueToTree(service.consultar(null, null, "resumo"));
        assertEquals(hoje.withDayOfMonth(1).toString(), resposta.get("dataInicial").asText());
        assertEquals(hoje.toString(), resposta.get("dataFinal").asText());
        assertEquals(0, resposta.get("dados").get("ticketMedio").asInt());
    }

    @Test
    void aceitaUmDiaEAte366Dias() {
        assertEquals(1, json.valueToTree(service.consultar("2026-10-01", "2026-10-01", "vendas")).get("dados").size());
        assertEquals(366, json.valueToTree(service.consultar("2024-01-01", "2024-12-31", "vendas")).get("dados").size());
    }

    private JsonNode consultar(String recurso) {
        return json.valueToTree(service.consultar("2026-10-01", "2026-10-03", recurso));
    }

    private void carregar(List<Pedido> lista) {
        when(pedidos.buscarPorPeriodo(any(), any())).thenReturn(lista);
    }

    private Pedido pedido(String id, String status, String total, int quantidade) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setStatus(status);
        pedido.setDataHoraEntrada(Instant.parse("2026-10-01T15:00:00Z"));
        pedido.setValorTotal(new BigDecimal(total));
        ItemPedido item = new ItemPedido();
        item.setProdutoId("pizza");
        item.setNome("Pizza");
        item.setQuantidade(quantidade);
        item.setPrecoUnitario(new BigDecimal("30.05"));
        item.setSubtotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(quantidade)));
        pedido.getItens().add(item);
        return pedido;
    }
}
