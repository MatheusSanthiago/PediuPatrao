package com.umc.pediupatrao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "mongodb.integration", matches = "true")
class FluxoCompletoTest {
    private static Process mongo;
    private static String uri;
    @Autowired private MockMvc mvc;
    @Autowired private MongoTemplate banco;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UsuarioDetailsService detalhes;
    @Autowired private ObjectMapper json;

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry propriedades) throws Exception {
        String executavel = System.getenv("MONGOD_PATH");
        if (executavel == null) throw new IllegalStateException("Defina MONGOD_PATH para os testes de integração.");
        Path dados = Files.createTempDirectory(Path.of("target"), "mongo-teste-");
        int porta;
        try (ServerSocket socket = new ServerSocket(0)) { porta = socket.getLocalPort(); }
        mongo = new ProcessBuilder(executavel, "--dbpath", dados.toAbsolutePath().toString(), "--bind_ip", "127.0.0.1",
                "--port", String.valueOf(porta), "--replSet", "teste")
                .redirectErrorStream(true).redirectOutput(dados.resolve("mongo.log").toFile()).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> mongo.destroy()));
        uri = "mongodb://127.0.0.1:" + porta + "/pizzaria_teste?replicaSet=teste";
        Files.writeString(Path.of("target", "mongo-verificacao.json"), new Document("uri", uri)
                .append("porta", porta).append("diretorio", dados.toAbsolutePath().toString()).toJson());
        try (MongoClient cliente = MongoClients.create("mongodb://127.0.0.1:" + porta + "/?directConnection=true&serverSelectionTimeoutMS=10000")) {
            cliente.getDatabase("admin").runCommand(new Document("replSetInitiate",
                    new Document("_id", "teste").append("members", List.of(new Document("_id", 0).append("host", "127.0.0.1:" + porta)))));
            long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (!Boolean.TRUE.equals(cliente.getDatabase("admin").runCommand(new Document("hello", 1)).getBoolean("isWritablePrimary"))) {
                if (System.nanoTime() > limite) throw new IllegalStateException("Replica set de teste não iniciou.");
                Thread.sleep(100);
            }
        }
        propriedades.add("spring.data.mongodb.uri", () -> uri);
        propriedades.add("spring.data.mongodb.database", () -> "pizzaria_teste");
        propriedades.add("app.admin.password", () -> "SenhaInicial123");
    }

    @BeforeEach
    void preparar() {
        assertEquals("pizzaria_teste", banco.getDb().getName());
        for (String colecao : List.of("pedidos", "clientes", "produtos", "auditoria", "usuarios", "funcionarios", "entregadores", "horariosAtendimento")) {
            banco.getCollection(colecao).deleteMany(new Document());
        }
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            Usuario usuario = new Usuario();
            usuario.setUsername(perfil.toLowerCase());
            usuario.setPassword(encoder.encode("SenhaTeste123"));
            usuario.setRole(perfil);
            usuarios.save(usuario);
        }
    }

    @AfterAll
    static void encerrar() throws Exception {
        if (mongo != null) {
            mongo.destroy();
            mongo.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (mongo.isAlive()) mongo.destroyForcibly();
        }
    }

    @Test
    void autenticaTresPerfisERenderizaTelas() throws Exception {
        for (String perfil : List.of("admin", "gerente", "atendente")) {
            org.springframework.mock.web.MockHttpSession sessao = (org.springframework.mock.web.MockHttpSession)
                    mvc.perform(formLogin().user(perfil).password("SenhaTeste123"))
                    .andExpect(authenticated().withUsername(perfil)).andReturn().getRequest().getSession(false);
            mvc.perform(get("/pedidos").session(sessao)).andExpect(status().isOk());
            for (String caminho : List.of("/", "/pedidos", "/produtos")) {
                mvc.perform(como(get(caminho), perfil)).andExpect(status().isOk());
            }
        }
        for (String caminho : List.of("/usuarios", "/usuarios/novo", "/auditoria", "/produtos/novo")) {
            mvc.perform(como(get(caminho), "admin")).andExpect(status().isOk());
        }
        for (String caminho : List.of("/clientes", "/clientes/novo", "/pedidos/novo")) {
            mvc.perform(como(get(caminho), "atendente")).andExpect(status().isOk());
        }
        mvc.perform(formLogin().user("admin").password("errada")).andExpect(unauthenticated());
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
    }

    @Test
    void bloqueiaApisPorPerfilEExigeCsrf() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(como(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"), "admin"))
                .andExpect(status().isForbidden());
        for (String perfil : List.of("atendente", "gerente")) {
            mvc.perform(como(get("/api/usuarios"), perfil)).andExpect(status().isForbidden());
            mvc.perform(como(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"intruso\",\"password\":\"SenhaTeste123\",\"role\":\"ADMIN\"}"), perfil)).andExpect(status().isForbidden());
        }
        for (String perfil : List.of("admin", "atendente")) {
            mvc.perform(como(put("/api/pedidos/inexistente/desconto").with(csrf()).param("percentual", "10"), perfil)).andExpect(status().isForbidden());
            mvc.perform(como(put("/api/pedidos/inexistente/cancelar").with(csrf()).param("justificativa", "Teste"), perfil)).andExpect(status().isForbidden());
            mvc.perform(como(put("/api/pedidos/inexistente/status").with(csrf()).param("status", "PRONTO"), perfil)).andExpect(status().isForbidden());
        }
        mvc.perform(como(get("/api/auditoria"), "atendente")).andExpect(status().isForbidden());
        mvc.perform(como(get("/clientes/novo"), "gerente")).andExpect(status().isForbidden());
        mvc.perform(como(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"), "gerente")).andExpect(status().isForbidden());
        mvc.perform(como(delete("/api/auditoria/qualquer").with(csrf()), "admin")).andExpect(status().isForbidden());
        mvc.perform(como(post("/api/auditoria").with(csrf()), "gerente")).andExpect(status().isForbidden());
        mvc.perform(como(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"), "admin")).andExpect(status().isForbidden());
    }

    @Test
    void percorrePedidoAteSaidaEFinalizacaoComAuditoria() throws Exception {
        String pedido = criarPedido();
        JsonNode recebido = json.readTree(mvc.perform(como(get("/api/pedidos"), "admin")).andReturn().getResponse().getContentAsString()).get(0);
        assertEquals("RECEBIDO", recebido.get("status").asText());
        assertEquals(60.10, recebido.get("valorTotal").asDouble());
        assertEquals("atendente", recebido.get("responsavelRegistro").asText());
        mvc.perform(como(put("/api/pedidos/" + pedido + "/status").with(csrf()).param("status", "FINALIZADO"), "gerente")).andExpect(status().isBadRequest());
        mvc.perform(como(put("/api/pedidos/" + pedido + "/desconto").with(csrf()).param("percentual", "20.01"), "gerente")).andExpect(status().isBadRequest());
        mvc.perform(como(put("/api/pedidos/" + pedido + "/desconto").with(csrf()).param("percentual", "20"), "gerente"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valorTotal").value(48.08));
        for (String estado : List.of("EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA", "FINALIZADO")) {
            mvc.perform(como(put("/api/pedidos/" + pedido + "/status").with(csrf()).param("status", estado), "gerente"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(estado));
        }
        Document salvo = banco.getCollection("pedidos").find().first();
        assertNotNull(salvo.get("dataHoraSaida"));
        assertEquals("gerente", salvo.getString("responsavelSaida"));
        assertEquals(6, banco.getCollection("auditoria").countDocuments(new Document("entidade", "PEDIDO")));
        mvc.perform(como(put("/api/pedidos/" + pedido + "/cancelar").with(csrf()).param("justificativa", "Tarde"), "gerente")).andExpect(status().isBadRequest());
        mvc.perform(como(put("/api/pedidos/" + pedido + "/desconto").with(csrf()).param("percentual", "10"), "gerente")).andExpect(status().isBadRequest());
        mvc.perform(como(get("/pedidos"), "gerente")).andExpect(status().isOk());
        mvc.perform(como(get("/auditoria"), "admin")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("ALTERACAO_STATUS")));
        mvc.perform(como(get("/api/auditoria").param("entidade", "PEDIDO").param("entidadeId", pedido), "admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void cancelamentoBloqueiaTodasAsAlteracoes() throws Exception {
        String pedido = criarPedido();
        mvc.perform(como(get("/pedidos"), "gerente")).andExpect(status().isOk());
        mvc.perform(como(put("/api/pedidos/" + pedido + "/cancelar").with(csrf()).param("justificativa", " "), "gerente")).andExpect(status().isBadRequest());
        mvc.perform(como(put("/api/pedidos/" + pedido + "/cancelar").with(csrf()).param("justificativa", "Cliente desistiu"), "gerente"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADO")).andExpect(jsonPath("$.itens.length()").value(1));
        mvc.perform(como(put("/api/pedidos/" + pedido + "/status").with(csrf()).param("status", "EM_PREPARACAO"), "gerente")).andExpect(status().isBadRequest());
        mvc.perform(como(put("/api/pedidos/" + pedido + "/desconto").with(csrf()).param("percentual", "10"), "gerente")).andExpect(status().isBadRequest());
        mvc.perform(como(put("/api/pedidos/" + pedido + "/cancelar").with(csrf()).param("justificativa", "Novamente"), "gerente")).andExpect(status().isBadRequest());
        assertEquals(2, banco.getCollection("auditoria").countDocuments(new Document("entidade", "PEDIDO")));
    }

    @Test
    void auditaAntesEDepoisDoTelefoneEEndereco() throws Exception {
        String cliente = criarCliente();
        mvc.perform(como(put("/api/clientes/" + cliente).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente teste\",\"telefone\":\"11999990000\",\"logradouro\":\"Av. Brasil\"}"), "atendente")).andExpect(status().isOk());
        Document registro = banco.getCollection("auditoria").find(new Document("operacao", "ALTERACAO_CLIENTE")).first();
        assertEquals("atendente", registro.getString("usuario"));
        assertEquals("ATENDENTE", registro.getString("perfil"));
        assertNotNull(registro.getDate("dataHora"));
        List<Document> alteracoes = registro.getList("alteracoes", Document.class);
        Document telefone = alteracoes.stream().filter(a -> a.getString("campo").equals("telefone")).findFirst().orElseThrow();
        assertEquals("11988887777", telefone.getString("valorAnterior"));
        assertEquals("11999990000", telefone.getString("valorNovo"));
        Document endereco = alteracoes.stream().filter(a -> a.getString("campo").equals("logradouro")).findFirst().orElseThrow();
        assertEquals("Rua das Flores", endereco.getString("valorAnterior"));
        assertEquals("Av. Brasil", endereco.getString("valorNovo"));
        mvc.perform(como(get("/clientes/editar/" + cliente), "atendente")).andExpect(status().isOk());
    }

    @Test
    void falhaNaAuditoriaDesfazAlteracaoDoPedido() throws Exception {
        String pedido = criarPedido();
        banco.getDb().runCommand(new Document("collMod", "auditoria").append("validator", new Document("operacao", "BLOQUEADO")));
        try {
            mvc.perform(como(put("/api/pedidos/" + pedido + "/status").with(csrf()).param("status", "EM_PREPARACAO"), "gerente")).andExpect(status().isServiceUnavailable());
            assertEquals("RECEBIDO", banco.getCollection("pedidos").find().first().getString("status"));
        } finally {
            banco.getDb().runCommand(new Document("collMod", "auditoria").append("validator", new Document()));
        }
    }

    @Test
    void usuariosNaoExpoemSenhaEPerfilMudaEmSessaoAberta() throws Exception {
        mvc.perform(como(get("/api/usuarios"), "admin")).andExpect(status().isOk()).andExpect(jsonPath("$[0].password").doesNotExist());
        String atendenteId = usuarios.findByUsername("atendente").orElseThrow().getId();
        mvc.perform(como(put("/api/usuarios/" + atendenteId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"atendente\",\"role\":\"GERENTE\",\"password\":\"\"}"), "admin")).andExpect(status().isOk());
        assertEquals("GERENTE", usuarios.findById(atendenteId).orElseThrow().getRole());
        assertTrue(encoder.matches("SenhaTeste123", usuarios.findById(atendenteId).orElseThrow().getPassword()));
        org.springframework.mock.web.MockHttpSession sessao = (org.springframework.mock.web.MockHttpSession) mvc.perform(
                formLogin().user("gerente").password("SenhaTeste123")).andReturn().getRequest().getSession(false);
        Usuario gerente = usuarios.findByUsername("gerente").orElseThrow();
        gerente.setRole("ATENDENTE");
        usuarios.save(gerente);
        mvc.perform(get("/api/auditoria").session(sessao)).andExpect(status().isForbidden());
    }

    @Test
    void cadastroDeProdutoRejeitaFracaoDeCentavo() throws Exception {
        mvc.perform(como(post("/api/produtos").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Pizza\",\"tipo\":\"Pizza\",\"preco\":0.001,\"ativo\":true}"), "admin"))
                .andExpect(status().isBadRequest());
        assertEquals(0, banco.getCollection("produtos").countDocuments());
    }

    @Test
    void versaoImpedeSobrescreverPedidoAlterado() throws Exception {
        String id = criarPedido();
        com.umc.pediupatrao.entity.Pedido copiaAntiga = banco.findById(id, com.umc.pediupatrao.entity.Pedido.class);
        mvc.perform(como(put("/api/pedidos/" + id + "/status").with(csrf()).param("status", "EM_PREPARACAO"), "gerente"))
                .andExpect(status().isOk());
        copiaAntiga.setStatus("CANCELADO");
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class, () -> banco.save(copiaAntiga));
        assertEquals("EM_PREPARACAO", banco.findById(id, com.umc.pediupatrao.entity.Pedido.class).getStatus());
    }


    @Test
    void preservaOpcoesOriginaisDoMenuParaTodosOsPerfis() throws Exception {
        for (String perfil : List.of("admin", "gerente", "atendente")) {
            String pagina = mvc.perform(como(get("/"), perfil)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            for (String opcao : List.of("Configurações", "Funcionários", "Entregadores", "Horário de Funcionamento", "Produtos", "Usuários")) {
                assertTrue(pagina.contains(opcao), perfil + ": opção ausente " + opcao);
            }
            assertTrue(pagina.contains("Perfil: " + perfil.toUpperCase()));
            for (String caminho : List.of("/configuracoes", "/funcionarios", "/entregadores", "/horarios")) {
                mvc.perform(como(get(caminho), perfil)).andExpect(status().isOk());
            }
        }
        mvc.perform(como(get("/usuarios"), "atendente")).andExpect(status().isForbidden());
        mvc.perform(como(get("/api/auditoria"), "atendente")).andExpect(status().isForbidden());
        mvc.perform(como(post("/horarios/salvar").with(csrf()), "atendente")).andExpect(status().isForbidden());
        mvc.perform(como(get("/entregadores"), "admin")).andExpect(content().string(
                org.hamcrest.Matchers.containsString("Novo entregador")));
    }

    @Test
    void filtraAuditoriaPorEntidadeMesmoSemIdentificador() throws Exception {
        criarPedido();
        mvc.perform(como(get("/api/auditoria").param("entidade", "CLIENTE"), "admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entidade").value("CLIENTE"));
        mvc.perform(como(get("/api/auditoria").param("entidade", "PEDIDO"), "gerente"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entidade").value("PEDIDO"));
        mvc.perform(como(get("/api/auditoria"), "admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(como(get("/api/auditoria").param("entidade", "USUARIO"), "admin")).andExpect(status().isBadRequest());
        mvc.perform(como(get("/api/auditoria").param("entidadeId", "id-sem-entidade"), "admin")).andExpect(status().isBadRequest());
        mvc.perform(como(get("/auditoria").param("entidade", "CLIENTE"), "admin")).andExpect(status().isOk());
    }

    @Test
    void preservaExclusaoDeClienteSemApagarHistorico() throws Exception {
        String id = criarCliente();
        mvc.perform(como(delete("/api/clientes/" + id).with(csrf()), "atendente")).andExpect(status().isNoContent());
        Document evento = banco.getCollection("auditoria").find(new Document("operacao", "EXCLUSAO_CLIENTE")).first();
        assertNotNull(evento);
        assertEquals(id, evento.getString("entidadeId"));
        assertEquals(2, banco.getCollection("auditoria").countDocuments(new Document("entidade", "CLIENTE")));
        criarPedido();
        String clienteComPedido = banco.getCollection("pedidos").find().first().getString("clienteId");
        mvc.perform(como(delete("/api/clientes/" + clienteComPedido).with(csrf()), "atendente")).andExpect(status().isBadRequest());
        assertEquals(1, banco.getCollection("clientes").countDocuments());
    }

    @Test
    void formulariosRegistramItensEDescontoComCsrf() throws Exception {
        criarPedido();
        Document pedidoOriginal = banco.getCollection("pedidos").find().first();
        String cliente = pedidoOriginal.getString("clienteId");
        String produto = pedidoOriginal.getList("itens", Document.class).getFirst().getString("produtoId");
        mvc.perform(como(post("/pedidos/salvar").with(csrf()).param("clienteId", cliente)
                .param("produtoId", produto, produto).param("quantidade", "2", "3"), "atendente"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/pedidos"));
        Document novoPedido = banco.getCollection("pedidos").find(new Document("_id", new Document("$ne", pedidoOriginal.get("_id")))).first();
        assertEquals(2, novoPedido.getList("itens", Document.class).size());
        assertEquals(new java.math.BigDecimal("150.25"), new java.math.BigDecimal(novoPedido.get("valorTotal").toString()));
        String id = novoPedido.getObjectId("_id").toHexString();
        mvc.perform(como(post("/pedidos/" + id + "/desconto").with(csrf()).param("percentual", "20"), "gerente"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/pedidos"));
        mvc.perform(como(get("/pedidos"), "gerente")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("120,20")));
    }

    @Test
    void alteracaoDePerfilPelaApiNaoPodeSerFeitaPorGerenteOuAtendente() throws Exception {
        String id = usuarios.findByUsername("atendente").orElseThrow().getId();
        for (String perfil : List.of("gerente", "atendente")) {
            mvc.perform(como(put("/api/usuarios/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"atendente\",\"role\":\"ADMIN\"}"), perfil)).andExpect(status().isForbidden());
            mvc.perform(como(post("/usuarios/editar/" + id).with(csrf()).param("username", "atendente").param("role", "ADMIN"), perfil))
                    .andExpect(status().isForbidden());
        }
        assertEquals("ATENDENTE", usuarios.findById(id).orElseThrow().getRole());
    }

    @Test
    void auditoriaNaoAceitaAtualizacaoNemExclusaoMesmoPorAdmin() throws Exception {
        criarPedido();
        String id = banco.getCollection("auditoria").find().first().getObjectId("_id").toHexString();
        for (String perfil : List.of("admin", "gerente", "atendente")) {
            mvc.perform(como(put("/api/auditoria/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"operacao\":\"FORJADA\"}"), perfil)).andExpect(status().isForbidden());
            mvc.perform(como(delete("/api/auditoria/" + id).with(csrf()), perfil)).andExpect(status().isForbidden());
        }
        assertEquals(2, banco.getCollection("auditoria").countDocuments());
    }

    @Test
    void cadastraEditaEInativaEquipeSemCriarContasDeAcesso() throws Exception {
        for (String tipo : List.of("funcionarios", "entregadores")) {
            String extra = tipo.equals("funcionarios") ? "cargo" : "veiculo";
            mvc.perform(como(get("/" + tipo + "/novo"), "admin")).andExpect(status().isOk());
            mvc.perform(como(post("/" + tipo + "/salvar").with(csrf())
                    .param("nome", "Ana Teste").param("telefone", "(11) 99999-1234")
                    .param(extra, "Equipe").param("ativo", "true"), "admin"))
                    .andExpect(redirectedUrl("/" + tipo));
            Document salvo = banco.getCollection(tipo).find().first();
            assertEquals("11999991234", salvo.getString("telefone"));
            String id = salvo.getObjectId("_id").toHexString();
            mvc.perform(como(get("/" + tipo + "/editar/" + id), "gerente"))
                    .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Teste")));
            mvc.perform(como(post("/" + tipo + "/salvar").with(csrf()).param("id", id)
                    .param("nome", "Ana Atualizada").param("telefone", "11999991234")
                    .param(extra, "Novo").param("ativo", "false"), "gerente"))
                    .andExpect(redirectedUrl("/" + tipo));
            salvo = banco.getCollection(tipo).find().first();
            assertEquals("Ana Atualizada", salvo.getString("nome"));
            assertFalse(salvo.getBoolean("ativo"));
            assertEquals(1, banco.getCollection(tipo).countDocuments());
            mvc.perform(como(get("/" + tipo), "atendente")).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Inativo")));
        }
        assertEquals(3, banco.getCollection("usuarios").countDocuments());
    }

    @Test
    void equipeRejeitaDadosInvalidosEMantemFormularioPreenchido() throws Exception {
        for (String tipo : List.of("funcionarios", "entregadores")) {
            mvc.perform(como(post("/" + tipo + "/salvar").with(csrf()).param("nome", "Ana")
                    .param("telefone", "123").param("cargo", "Caixa").param("veiculo", "Moto"), "gerente"))
                    .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Informe um telefone")));
            assertEquals(0, banco.getCollection(tipo).countDocuments());
        }
    }

    @Test
    void novasConfiguracoesExigemPerfilECsrf() throws Exception {
        for (String tipo : List.of("funcionarios", "entregadores", "horarios")) {
            mvc.perform(como(post("/" + tipo + "/salvar").with(csrf()), "atendente")).andExpect(status().isForbidden());
            mvc.perform(como(post("/" + tipo + "/salvar"), "admin")).andExpect(status().isForbidden());
        }
        for (String tipo : List.of("funcionarios", "entregadores")) {
            mvc.perform(como(get("/" + tipo + "/novo"), "atendente")).andExpect(status().isForbidden());
        }
        for (String perfil : List.of("admin", "atendente")) {
            mvc.perform(como(post("/entregas/qualquer/despachar").with(csrf()).param("entregadorId", "qualquer"), perfil))
                    .andExpect(status().isForbidden());
            mvc.perform(como(post("/entregas/qualquer/finalizar").with(csrf()), perfil)).andExpect(status().isForbidden());
        }
    }

    @Test
    void salvaHorarioNoturnoEDiaFechadoSemDuplicarSemana() throws Exception {
        mvc.perform(como(get("/horarios"), "admin")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Segunda-feira")));
        mvc.perform(como(post("/horarios/salvar").with(csrf()).param("id", "MONDAY")
                .param("fechado", "false").param("abertura", "18:00").param("fechamento", "02:00"), "gerente"))
                .andExpect(redirectedUrl("/horarios")).andExpect(flash().attributeExists("sucesso"));
        assertEquals("02:00", banco.getCollection("horariosAtendimento").find().first().getString("fechamento"));
        mvc.perform(como(get("/horarios"), "atendente")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fechamento no dia seguinte.")));
        for (String abertura : List.of("", "25:00", "18:00")) {
            mvc.perform(como(post("/horarios/salvar").with(csrf()).param("id", "MONDAY")
                    .param("fechado", "false").param("abertura", abertura).param("fechamento", "18:00"), "admin"))
                    .andExpect(flash().attributeExists("erro"));
        }
        mvc.perform(como(post("/horarios/salvar").with(csrf()).param("id", "INVALIDO").param("fechado", "true"), "admin"))
                .andExpect(flash().attributeExists("erro"));
        assertEquals("02:00", banco.getCollection("horariosAtendimento").find().first().getString("fechamento"));
        mvc.perform(como(post("/horarios/salvar").with(csrf()).param("id", "MONDAY").param("fechado", "true"), "admin"))
                .andExpect(flash().attributeExists("sucesso"));
        assertEquals(1, banco.getCollection("horariosAtendimento").countDocuments());
        Document fechado = banco.getCollection("horariosAtendimento").find().first();
        assertTrue(fechado.getBoolean("fechado"));
        assertNull(fechado.get("abertura"));
    }

    @Test
    void despachaComEntregadorAtivoEFinalizaPreservandoHistorico() throws Exception {
        String pedido = criarPedido();
        mvc.perform(como(post("/entregadores/salvar").with(csrf()).param("nome", "João Entregador")
                .param("telefone", "11999991234").param("veiculo", "Moto").param("ativo", "false"), "gerente"))
                .andExpect(redirectedUrl("/entregadores"));
        Document entregador = banco.getCollection("entregadores").find().first();
        String id = entregador.getObjectId("_id").toHexString();
        mvc.perform(como(post("/entregas/" + pedido + "/despachar").with(csrf()).param("entregadorId", id), "gerente"))
                .andExpect(status().isBadRequest());
        for (String estado : List.of("EM_PREPARACAO", "PRONTO")) {
            mvc.perform(como(put("/api/pedidos/" + pedido + "/status").with(csrf()).param("status", estado), "gerente"))
                    .andExpect(status().isOk());
        }
        mvc.perform(como(get("/entregas"), "gerente")).andExpect(status().isOk());
        mvc.perform(como(post("/entregas/" + pedido + "/despachar").with(csrf()).param("entregadorId", id), "gerente"))
                .andExpect(status().isBadRequest());
        banco.getCollection("entregadores").updateOne(new Document("_id", entregador.get("_id")),
                new Document("$set", new Document("ativo", true)));
        mvc.perform(como(get("/entregas"), "gerente")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("João Entregador")));
        mvc.perform(como(post("/entregas/" + pedido + "/despachar").with(csrf()).param("entregadorId", id), "gerente"))
                .andExpect(redirectedUrl("/entregas"));
        Document salvo = banco.getCollection("pedidos").find().first();
        assertEquals("SAIU_PARA_ENTREGA", salvo.getString("status"));
        assertEquals(id, salvo.getString("entregadorId"));
        assertEquals("João Entregador", salvo.getString("entregadorNome"));
        assertNotNull(salvo.getDate("dataHoraSaida"));
        assertEquals(1, banco.getCollection("auditoria").countDocuments(new Document("operacao", "ATRIBUICAO_ENTREGADOR")));
        mvc.perform(como(post("/entregas/" + pedido + "/despachar").with(csrf()).param("entregadorId", id), "gerente"))
                .andExpect(status().isBadRequest());
        mvc.perform(como(post("/entregas/" + pedido + "/finalizar").with(csrf()), "gerente"))
                .andExpect(redirectedUrl("/entregas"));
        mvc.perform(como(post("/entregas/" + pedido + "/finalizar").with(csrf()), "gerente"))
                .andExpect(status().isBadRequest());
        banco.getCollection("entregadores").updateOne(new Document("_id", entregador.get("_id")),
                new Document("$set", new Document("nome", "Nome alterado")));
        for (String perfil : List.of("admin", "gerente", "atendente")) {
            mvc.perform(como(get("/entregas"), perfil)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("João Entregador")));
        }
        assertEquals("FINALIZADO", banco.getCollection("pedidos").find().first().getString("status"));
    }

    @Test
    void falhaNaAuditoriaDesfazDespachoEAtribuicao() throws Exception {
        String pedido = criarPedido();
        for (String estado : List.of("EM_PREPARACAO", "PRONTO")) {
            mvc.perform(como(put("/api/pedidos/" + pedido + "/status").with(csrf()).param("status", estado), "gerente"))
                    .andExpect(status().isOk());
        }
        banco.getCollection("entregadores").insertOne(new Document("_id", "entregador-teste")
                .append("nome", "Teste").append("ativo", true));
        banco.getDb().runCommand(new Document("collMod", "auditoria").append("validator", new Document("operacao", "BLOQUEADO")));
        try {
            mvc.perform(como(post("/entregas/" + pedido + "/despachar").with(csrf())
                    .param("entregadorId", "entregador-teste"), "gerente")).andExpect(status().isServiceUnavailable());
            Document salvo = banco.getCollection("pedidos").find().first();
            assertEquals("PRONTO", salvo.getString("status"));
            assertNull(salvo.get("entregadorId"));
        } finally {
            banco.getDb().runCommand(new Document("collMod", "auditoria").append("validator", new Document()));
        }
    }
    private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder request, String usuario) {
        return request.with(user(detalhes.loadUserByUsername(usuario)));
    }

    private String criarCliente() throws Exception {
        String resposta = mvc.perform(como(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente teste\",\"telefone\":\"11988887777\",\"logradouro\":\"Rua das Flores\"}"), "atendente"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("id").asText();
    }

    private String criarPedido() throws Exception {
        String cliente = criarCliente();
        String produtoJson = mvc.perform(como(post("/api/produtos").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Pizza teste\",\"tipo\":\"Pizza\",\"preco\":30.05,\"ativo\":true}"), "admin"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String produto = json.readTree(produtoJson).get("id").asText();
        String corpo = "{\"clienteId\":\"" + cliente + "\",\"status\":\"FINALIZADO\",\"valorTotal\":1,\"descontoPercentual\":100,"
                + "\"responsavelRegistro\":\"forjado\",\"itens\":[{\"produtoId\":\"" + produto + "\",\"quantidade\":2,\"precoUnitario\":1}]}";
        String resposta = mvc.perform(como(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corpo), "atendente"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("id").asText();
    }
}
