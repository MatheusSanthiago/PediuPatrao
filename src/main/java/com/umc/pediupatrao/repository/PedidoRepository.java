package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.time.Instant;
import org.springframework.data.mongodb.repository.Query;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);
    @Query("{ 'dataHoraEntrada': { $gte: ?0, $lt: ?1 } }")
    List<Pedido> buscarPorPeriodo(Instant inicio, Instant fim);
    long countByDataHoraEntradaIsNull();
}
