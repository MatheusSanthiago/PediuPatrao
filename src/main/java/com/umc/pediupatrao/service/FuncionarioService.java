package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Funcionario;
import com.umc.pediupatrao.repository.FuncionarioRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
public class FuncionarioService {
    private final FuncionarioRepository repository;

    public FuncionarioService(FuncionarioRepository repository) {
        this.repository = repository;
    }

    public List<Funcionario> listar() {
        return repository.findAllByOrderByNomeAsc();
    }

    public Funcionario buscar(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Funcionário não encontrado."));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public void salvar(Funcionario dados) {
        Funcionario registro = dados.getId() == null || dados.getId().isBlank() ? new Funcionario() : buscar(dados.getId());
        registro.setNome(ValidacaoEquipe.texto(dados.getNome(), "o nome", 120));
        registro.setTelefone(ValidacaoEquipe.telefone(dados.getTelefone()));
        registro.setCargo(ValidacaoEquipe.texto(dados.getCargo(), "o cargo", 80));
        registro.setAtivo(dados.isAtivo());
        repository.save(registro);
    }
}