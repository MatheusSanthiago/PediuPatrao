package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Alteracao;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {
    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    public void registrar(String operacao, String entidade, String id, Map<String, String> antes,
                          Map<String, String> depois, String justificativa) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(autenticacao.getName());
        auditoria.setPerfil(autenticacao.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
        auditoria.setDataHora(Instant.now());
        auditoria.setOperacao(operacao);
        auditoria.setEntidade(entidade);
        auditoria.setEntidadeId(id);
        auditoria.setJustificativa(justificativa);
        for (String campo : depois.keySet()) {
            if (!Objects.equals(antes.get(campo), depois.get(campo))) {
                Alteracao alteracao = new Alteracao();
                alteracao.setCampo(campo);
                alteracao.setValorAnterior(antes.get(campo));
                alteracao.setValorNovo(depois.get(campo));
                auditoria.getAlteracoes().add(alteracao);
            }
        }
        auditoriaRepository.insert(auditoria);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public Page<Auditoria> listar(String entidade, String entidadeId, int pagina) {
        if (pagina < 0) {
            throw new IllegalArgumentException("A página deve ser zero ou maior.");
        }
        boolean temEntidade = entidade != null && !entidade.isBlank();
        if (temEntidade && !"PEDIDO".equals(entidade) && !"CLIENTE".equals(entidade)) {
            throw new IllegalArgumentException("Selecione PEDIDO ou CLIENTE.");
        }
        PageRequest paginacao = PageRequest.of(pagina, 30);
        if (entidadeId != null && !entidadeId.isBlank()) {
            if (!temEntidade) {
                throw new IllegalArgumentException("Selecione PEDIDO ou CLIENTE para filtrar pelo identificador.");
            }
            return auditoriaRepository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade, entidadeId, paginacao);
        }
        if (temEntidade) {
            return auditoriaRepository.findByEntidadeOrderByDataHoraDesc(entidade, paginacao);
        }
        return auditoriaRepository.findAllByOrderByDataHoraDesc(paginacao);
    }
}
