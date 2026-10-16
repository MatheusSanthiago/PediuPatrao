package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PedidoServiceTest {
    private PedidoRepository pedidos;
    private ProdutoRepository produtos;
    private ClienteRepository clientes;
    private AuditoriaService auditoria;
    private PedidoService service;

    @BeforeEach
    void preparar() {
        pedidos = mock(PedidoRepository.class);
        produtos = mock(ProdutoRepository.class);
        clientes = mock(ClienteRepository.class);
        auditoria = mock(AuditoriaService.class);
        service = new PedidoService(pedidos, clientes, produtos, auditoria);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("gerente", null, List.of()));
        when(pedidos.save(any())).thenAnswer(chamada -> {
            Pedido pedido = chamada.getArgument(0);
            pedido.setId("pedido-1");
            return pedido;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoraPrecoTotalDescontoResponsavelEStatusEnviadosPeloCliente() {
        when(clientes.existsById("cliente-1")).thenReturn(true);
        Produto produto = new Produto();
        produto.setId("produto-1");
        produto.setNome("Pizza");
        produto.setAtivo(true);
        produto.setPreco(30.05);
        when(produtos.findById("produto-1")).thenReturn(Optional.of(produto));
        Pedido dados = new Pedido();
        dados.setId("id-forjado");
        dados.setClienteId("cliente-1");
        dados.setStatus("FINALIZADO");
        dados.setValorTotal(BigDecimal.ONE);
        dados.setDescontoPercentual(new BigDecimal("100"));
        dados.setResponsavelRegistro("outra-pessoa");
        ItemPedido item = new ItemPedido();
        item.setProdutoId("produto-1");
        item.setQuantidade(3);
        item.setPrecoUnitario(BigDecimal.ONE);
        dados.setItens(List.of(item));

        Pedido salvo = service.criarPedido(dados);

        assertEquals(new BigDecimal("90.15"), salvo.getValorTotal());
        assertEquals(new BigDecimal("30.05"), salvo.getItens().getFirst().getPrecoUnitario());
        assertEquals(BigDecimal.ZERO, salvo.getDescontoPercentual());
        assertEquals("RECEBIDO", salvo.getStatus());
        assertEquals("gerente", salvo.getResponsavelRegistro());
        assertNotNull(salvo.getDataHoraEntrada());
        assertNull(salvo.getDataHoraSaida());
        verify(auditoria).registrar(eq("INCLUSAO_PEDIDO"), eq("PEDIDO"), eq("pedido-1"), anyMap(), anyMap(), isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "20.01", "100", "0.001"})
    void rejeitaDescontoInvalido(String percentual) {
        assertThrows(IllegalArgumentException.class, () -> service.aplicarDesconto("pedido-1", new BigDecimal(percentual)));
        verify(pedidos, never()).save(any());
    }

    @Test
    void descontoUsaSubtotalOriginalESubstituiDescontoAnterior() {
        Pedido pedido = pedido("PRONTO");
        when(pedidos.findById("pedido-1")).thenReturn(Optional.of(pedido));
        service.aplicarDesconto("pedido-1", new BigDecimal("20"));
        assertEquals(new BigDecimal("16.00"), pedido.getValorTotal());
        service.aplicarDesconto("pedido-1", new BigDecimal("10"));
        assertEquals(new BigDecimal("18.00"), pedido.getValorTotal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO"})
    void bloqueiaDescontoECancelamentoDepoisDaSaidaOuEncerramento(String status) {
        when(pedidos.findById("pedido-1")).thenReturn(Optional.of(pedido(status)));
        assertThrows(IllegalArgumentException.class, () -> service.aplicarDesconto("pedido-1", BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class, () -> service.cancelarPedido("pedido-1", "Cliente desistiu"));
        verify(pedidos, never()).save(any());
    }

    @Test
    void validaTodasAsCombinacoesDeStatus() {
        List<String> estados = List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO");
        List<String> permitidas = List.of("RECEBIDO>EM_PREPARACAO", "EM_PREPARACAO>PRONTO", "PRONTO>SAIU_PARA_ENTREGA",
                "PRONTO>RETIRADO", "SAIU_PARA_ENTREGA>FINALIZADO", "RETIRADO>FINALIZADO");
        for (String atual : estados) {
            for (String proximo : estados) {
                Pedido pedido = pedido(atual);
                when(pedidos.findById("pedido-1")).thenReturn(Optional.of(pedido));
                if (permitidas.contains(atual + ">" + proximo)) {
                    assertEquals(proximo, service.atualizarStatus("pedido-1", proximo).getStatus());
                } else {
                    assertThrows(IllegalArgumentException.class, () -> service.atualizarStatus("pedido-1", proximo), atual + ">" + proximo);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETIRADO", "SAIU_PARA_ENTREGA"})
    void registraHorarioEResponsavelNaSaida(String status) {
        Pedido pedido = pedido("PRONTO");
        when(pedidos.findById("pedido-1")).thenReturn(Optional.of(pedido));
        service.atualizarStatus("pedido-1", status);
        assertNotNull(pedido.getDataHoraSaida());
        assertEquals("gerente", pedido.getResponsavelSaida());
    }

    @Test
    void cancelamentoExigeJustificativaEPreservaItens() {
        Pedido pedido = pedido("RECEBIDO");
        when(pedidos.findById("pedido-1")).thenReturn(Optional.of(pedido));
        assertThrows(IllegalArgumentException.class, () -> service.cancelarPedido("pedido-1", " "));
        service.cancelarPedido("pedido-1", "Cliente desistiu");
        assertEquals("CANCELADO", pedido.getStatus());
        assertEquals(1, pedido.getItens().size());
        assertEquals("Cliente desistiu", pedido.getJustificativaCancelamento());
        verify(auditoria).registrar(eq("CANCELAMENTO_PEDIDO"), eq("PEDIDO"), eq("pedido-1"),
                anyMap(), anyMap(), eq("Cliente desistiu"));
    }

    @Test
    void rejeitaPedidoSemItensOuCliente() {
        assertThrows(IllegalArgumentException.class, () -> service.criarPedido(new Pedido()));
        when(clientes.existsById("cliente-1")).thenReturn(true);
        Pedido dados = new Pedido();
        dados.setClienteId("cliente-1");
        assertThrows(IllegalArgumentException.class, () -> service.criarPedido(dados));
    }

    private Pedido pedido(String status) {
        Pedido pedido = new Pedido();
        pedido.setId("pedido-1");
        pedido.setStatus(status);
        pedido.setValorSubtotal(new BigDecimal("20.00"));
        pedido.setValorTotal(new BigDecimal("20.00"));
        ItemPedido item = new ItemPedido();
        item.setQuantidade(1);
        pedido.setItens(List.of(item));
        return pedido;
    }
}
