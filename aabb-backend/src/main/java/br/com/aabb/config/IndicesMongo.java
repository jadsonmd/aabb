package br.com.aabb.config;

import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import br.com.aabb.jogador.Jogador;
import br.com.aabb.partida.Partida;
import br.com.aabb.presenca.Presenca;
import br.com.aabb.lance.Lance;
import br.com.aabb.whatsapp.MensagemProcessada;
import br.com.aabb.whatsapp.WebhookBruto;

/**
 * Cria os índices do MongoDB na subida da aplicação (operação idempotente).
 */
@Component
class IndicesMongo {

	private final MongoTemplate mongo;

	IndicesMongo(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@EventListener(ApplicationReadyEvent.class)
	void criar() {
		// Versão anterior tinha pré-cadastro com aprovação: pendentes viram membros ativos.
		mongo.updateMulti(Query.query(Criteria.where("pendente").is(true)),
				new Update().set("ativo", true).unset("pendente"), Jogador.class);
		mongo.updateMulti(Query.query(Criteria.where("pendente").exists(true)), new Update().unset("pendente"),
				Jogador.class);

		var jogadores = mongo.indexOps(Jogador.class);
		jogadores.createIndex(new Index("whatsapp", Sort.Direction.ASC).unique());
		// Só indexa documentos que tenham ao menos um LID (array vazio não pode colidir).
		jogadores.createIndex(new Index("lids", Sort.Direction.ASC).unique()
				.partial(PartialIndexFilter.of(new Document("lids", new Document("$type", "string")))));

		mongo.indexOps(Partida.class).createIndex(new Index("data", Sort.Direction.ASC).unique());

		mongo.indexOps(Presenca.class).createIndex(new Index()
				.on("jogadorId", Sort.Direction.ASC).on("partidaId", Sort.Direction.ASC).unique());

		var lances = mongo.indexOps(Lance.class);
		lances.createIndex(new Index("messageId", Sort.Direction.ASC).unique());
		// Um único GOL aprovado por jogador por partida.
		lances.createIndex(new Index()
				.on("partidaId", Sort.Direction.ASC).on("autor.jogadorId", Sort.Direction.ASC)
				.unique()
				.named("gol_aprovado_unico")
				.partial(PartialIndexFilter.of(Criteria.where("tipo").is("GOL").and("status").is("APPROVED"))));

		mongo.indexOps(MensagemProcessada.class)
				.createIndex(new Index("processadaEm", Sort.Direction.ASC).expire(java.time.Duration.ofDays(7)));
		mongo.indexOps(WebhookBruto.class)
				.createIndex(new Index("recebidoEm", Sort.Direction.ASC).expire(java.time.Duration.ofDays(7)));
	}
}
