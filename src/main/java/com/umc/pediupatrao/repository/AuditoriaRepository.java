package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface AuditoriaRepository extends Repository<Auditoria, String> {
    Auditoria insert(Auditoria auditoria);
    Page<Auditoria> findAllByOrderByDataHoraDesc(Pageable pageable);
    Page<Auditoria> findByEntidadeOrderByDataHoraDesc(String entidade, Pageable pageable);
    Page<Auditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId, Pageable pageable);
}
