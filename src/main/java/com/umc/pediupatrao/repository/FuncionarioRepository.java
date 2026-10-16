package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Funcionario;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FuncionarioRepository extends MongoRepository<Funcionario, String> {
    List<Funcionario> findAllByOrderByNomeAsc();
}