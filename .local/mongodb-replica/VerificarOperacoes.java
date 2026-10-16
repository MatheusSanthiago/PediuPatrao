import com.umc.pediupatrao.PizzariaPedidosApplication;
import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.service.*;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.springframework.boot.SpringApplication;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.math.BigDecimal;

public class VerificarOperacoes {
    static void perfil(String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "verificacao_local", null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }
    static void exigir(boolean condicao, String erro) {
        if (!condicao) throw new IllegalStateException(erro);
    }
    public static void main(String[] args) {
        String nome = "pizzaria_verificacao_rs0_" + UUID.randomUUID().toString().replace("-", "");
        String uri = "mongodb://localhost:27017/" + nome + "?replicaSet=rs0";
        try (var cliente = MongoClients.create(uri)) {
            exigir(!cliente.listDatabaseNames().into(new ArrayList<>()).contains(nome), "Banco de teste já existe.");
            try (var contexto = SpringApplication.run(PizzariaPedidosApplication.class,
                    "--server.port=0", "--spring.data.mongodb.uri=" + uri,
                    "--spring.data.mongodb.database=" + nome,
                    "--app.admin.password=" + UUID.randomUUID(),
                    "--logging.level.root=WARN", "--spring.main.banner-mode=off")) {
                MongoTemplate banco = contexto.getBean(MongoTemplate.class);
                exigir(nome.equals(banco.getDb().getName()), "Banco incorreto.");
                var produtos = contexto.getBean(ProdutoService.class);
                var clientes = contexto.getBean(ClienteService.class);
                var pedidos = contexto.getBean(PedidoService.class);
                var entregadores = contexto.getBean(EntregadorService.class);
                var entregas = contexto.getBean(EntregaService.class);
                perfil("ADMIN");
                Produto produto = new Produto();
                produto.setNome("Produto de verificação");
                produto.setTipo("Pizza");
                produto.setPreco(40.00);
                produto.setAtivo(true);
                produto = produtos.novoProduto(produto);
                perfil("ATENDENTE");
                Cliente pessoa = new Cliente();
                pessoa.setNome("Cliente de verificação");
                pessoa.setTelefone("11900000000");
                pessoa = clientes.novoCliente(pessoa);
                ItemPedido item = new ItemPedido();
                item.setProdutoId(produto.getId());
                item.setQuantidade(2);
                Pedido dados = new Pedido();
                dados.setClienteId(pessoa.getId());
                dados.setItens(List.of(item));
                Pedido pedido = pedidos.criarPedido(dados);
                exigir(pedido.getValorTotal().compareTo(new BigDecimal("80.00")) == 0, "Total incorreto.");
                perfil("GERENTE");
                Entregador entregador = new Entregador();
                entregador.setNome("Entregador de verificação");
                entregador.setTelefone("11900000001");
                entregador.setVeiculo("Moto");
                entregadores.salvar(entregador);
                entregador = entregadores.listar().getFirst();
                pedidos.aplicarDesconto(pedido.getId(), new BigDecimal("10"));
                pedidos.atualizarStatus(pedido.getId(), "EM_PREPARACAO");
                pedidos.atualizarStatus(pedido.getId(), "PRONTO");
                entregas.despachar(pedido.getId(), entregador.getId());
                entregas.finalizar(pedido.getId());
                Pedido salvo = banco.findById(pedido.getId(), Pedido.class);
                exigir("FINALIZADO".equals(salvo.getStatus()), "Entrega não finalizou.");
                exigir(entregador.getId().equals(salvo.getEntregadorId()), "Entregador ausente.");
                exigir(salvo.getValorTotal().compareTo(new BigDecimal("72.00")) == 0, "Desconto incorreto.");
                exigir(salvo.getDataHoraSaida() != null, "Saída ausente.");
                long eventos = banco.getCollection("auditoria").countDocuments(new Document("entidadeId", pedido.getId()));
                exigir(eventos == 7, "Histórico incompleto: " + eventos);
                System.out.println("APROVADO: cadastro de cliente, pedido, desconto, preparo, despacho, finalização e 7 eventos de auditoria persistidos.");

                Pedido cancelado = pedidos.criarPedido(dados);
                pedidos.cancelarPedido(cancelado.getId(), "Verificação local");
                exigir("CANCELADO".equals(banco.findById(cancelado.getId(), Pedido.class).getStatus()), "Cancelamento não persistiu.");
                System.out.println("APROVADO: cancelamento com histórico.");

                Pedido rollback = pedidos.criarPedido(dados);
                banco.getDb().runCommand(new Document("collMod", "auditoria")
                    .append("validator", new Document("operacao", "BLOQUEADO_PARA_TESTE")));
                boolean falhou = false;
                try { pedidos.atualizarStatus(rollback.getId(), "EM_PREPARACAO"); }
                catch (org.springframework.dao.DataAccessException esperado) { falhou = true; }
                exigir(falhou, "A falha controlada não ocorreu.");
                exigir("RECEBIDO".equals(banco.findById(rollback.getId(), Pedido.class).getStatus()),
                    "Transação não desfez a alteração.");
                System.out.println("APROVADO: falha na auditoria desfez a alteração do pedido.");
            } finally {
                SecurityContextHolder.clearContext();
                if (!nome.startsWith("pizzaria_verificacao_rs0_")) throw new IllegalStateException("Nome inesperado.");
                cliente.getDatabase(nome).drop();
                System.out.println("Banco temporário de verificação removido. Banco pizzaria não foi alterado.");
            }
        }
    }
}