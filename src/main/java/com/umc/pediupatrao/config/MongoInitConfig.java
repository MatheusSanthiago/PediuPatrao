package com.umc.pediupatrao.config;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Configuration
public class MongoInitConfig implements CommandLineRunner {
    private final MongoTemplate mongoTemplate;

    public MongoInitConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Override
    public void run(String... args) {
        for (String colecao : List.of("pedidos", "clientes", "usuarios", "produtos", "horariosAtendimento", "auditoria")) {
            if (!mongoTemplate.collectionExists(colecao)) {
                mongoTemplate.createCollection(colecao);
            }
        }
        mongoTemplate.indexOps("usuarios").ensureIndex(new Index().on("username", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("auditoria").ensureIndex(new Index().on("dataHora", Sort.Direction.DESC));
        // inclui controle de versão nos registros anteriores à atividade
        for (String colecao : List.of("pedidos", "clientes")) {
            mongoTemplate.updateMulti(new Query(Criteria.where("versao").is(null)), new Update().set("versao", 0L), colecao);
        }
    }
}
