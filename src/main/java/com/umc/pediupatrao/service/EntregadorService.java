package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Entregador;
import com.umc.pediupatrao.repository.EntregadorRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
public class EntregadorService {
    private final EntregadorRepository repository;

    public EntregadorService(EntregadorRepository repository) {
        this.repository = repository;
    }

    public List<Entregador> listar() {
        return repository.findAllByOrderByNomeAsc();
    }

    public Entregador buscar(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Entregador não encontrado."));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public void salvar(Entregador dados) {
        Entregador registro = dados.getId() == null || dados.getId().isBlank() ? new Entregador() : buscar(dados.getId());
        registro.setNome(ValidacaoEquipe.texto(dados.getNome(), "o nome", 120));
        registro.setTelefone(ValidacaoEquipe.telefone(dados.getTelefone()));
        registro.setVeiculo(ValidacaoEquipe.texto(dados.getVeiculo(), "o veiculo", 80));
        registro.setAtivo(dados.isAtivo());
        repository.save(registro);
    }
}