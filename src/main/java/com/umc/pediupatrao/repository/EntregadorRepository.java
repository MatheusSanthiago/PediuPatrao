package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Entregador;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EntregadorRepository extends MongoRepository<Entregador, String> {
    List<Entregador> findAllByOrderByNomeAsc();
}