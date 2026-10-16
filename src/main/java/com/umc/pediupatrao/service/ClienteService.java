package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {
    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente novoCliente(Cliente cliente) {
        cliente.setId(null);
        return salvar(cliente);
    }

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @Transactional
    @PreAuthorize("hasRole('ATENDENTE')")
    public void excluir(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
        if (!pedidoRepository.findByClienteId(id).isEmpty()) {
            throw new IllegalArgumentException("Clientes com pedidos não podem ser excluídos.");
        }
        Map<String, String> antes = dadosAuditoria(cliente);
        Map<String, String> depois = new LinkedHashMap<>();
        for (String campo : antes.keySet()) {
            depois.put(campo, null);
        }
        clienteRepository.delete(cliente);
        auditoriaService.registrar("EXCLUSAO_CLIENTE", "CLIENTE", id, antes, depois, null);
    }

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @Transactional
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente cliente) {
        if (cliente.getNome() == null || cliente.getNome().isBlank() || cliente.getNome().length() > 120
                || cliente.getTelefone() == null || !cliente.getTelefone().replaceAll("[^0-9]", "").matches("[0-9]{10,11}")) {
            throw new IllegalArgumentException("Informe o nome (até 120 caracteres) e telefone com DDD (10 ou 11 dígitos).");
        }
        Map<String, String> antes = new LinkedHashMap<>();
        boolean novo = cliente.getId() == null;
        if (!novo) {
            Cliente existente = clienteRepository.findById(cliente.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para atualização."));
            antes = dadosAuditoria(existente);
            // a versão é lida do banco para não confiar no formulário
            cliente.setVersao(existente.getVersao());
            if (cliente.getEndereco() == null) {
                cliente.setEndereco(existente.getEndereco());
            }
        } else {
            cliente.setVersao(null);
        }
        cliente.setNome(cliente.getNome().trim());
        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrar(novo ? "INCLUSAO_CLIENTE" : "ALTERACAO_CLIENTE", "CLIENTE",
                salvo.getId(), antes, dadosAuditoria(salvo), null);
        return salvo;
    }

    private Map<String, String> dadosAuditoria(Cliente cliente) {
        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("nome", cliente.getNome());
        dados.put("telefone", cliente.getTelefone());
        dados.put("cep", cliente.getCep());
        dados.put("logradouro", cliente.getLogradouro());
        dados.put("numero", cliente.getNumero());
        dados.put("complemento", cliente.getComplemento());
        dados.put("bairro", cliente.getBairro());
        dados.put("cidade", cliente.getCidade());
        dados.put("estado", cliente.getEstado());
        dados.put("endereco", cliente.getEndereco());
        return dados;
    }
}
