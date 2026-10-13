package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.HorarioFuncionamento;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HorarioFuncionamentoRepository extends MongoRepository<HorarioFuncionamento, String> {
}