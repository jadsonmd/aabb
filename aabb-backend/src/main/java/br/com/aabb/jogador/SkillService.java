package br.com.aabb.jogador;

import java.time.Clock;
import java.util.Collection;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import br.com.aabb.config.AabbProperties;

/**
 * Ajusta a skill dos membros respeitando os limites configurados (0.0 a 10.0).
 * Quando chamado dentro de uma transação, participa dela.
 */
@Service
public class SkillService {

	private final MongoTemplate mongo;
	private final AabbProperties.Skill config;
	private final Clock clock;

	SkillService(MongoTemplate mongo, AabbProperties properties, Clock clock) {
		this.mongo = mongo;
		this.config = properties.skill();
		this.clock = clock;
	}

	public void ajustar(Collection<String> jogadorIds, double delta, String motivo, String origemId) {
		if (jogadorIds.isEmpty() || delta == 0) {
			return;
		}
		// skill = round(min(max, max(min, skill + delta)), 2), calculado no próprio MongoDB (atômico).
		Document novaSkill = new Document("$round", List.of(
				new Document("$min", List.of(config.maximo(),
						new Document("$max", List.of(config.minimo(),
								new Document("$add", List.of("$skill", delta)))))),
				2));
		AggregationOperation set = contexto -> new Document("$set", new Document("skill", novaSkill));
		mongo.updateMulti(
				Query.query(Criteria.where("_id").in(jogadorIds)),
				AggregationUpdate.from(List.of(set)),
				Jogador.class);
		var agora = clock.instant();
		List<HistoricoSkill> historico = jogadorIds.stream()
				.map(id -> new HistoricoSkill(null, id, delta, motivo, origemId, agora))
				.toList();
		mongo.insertAll(historico);
	}
}
