package br.com.aabb.presenca;

import java.time.Clock;
import java.util.Collection;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import br.com.aabb.partida.Partida;

/**
 * Presenças: computadas no {@code #sortear} para os membros sorteados (titulares e reservas)
 * e removidas se a partida for cancelada.
 */
@Service
public class PresencaService {

	private final MongoTemplate mongo;
	private final Clock clock;

	PresencaService(MongoTemplate mongo, Clock clock) {
		this.mongo = mongo;
		this.clock = clock;
	}

	public void registrar(Partida partida, Collection<String> jogadorIds) {
		var agora = clock.instant();
		for (String jogadorId : jogadorIds) {
			mongo.upsert(
					Query.query(Criteria.where("jogadorId").is(jogadorId).and("partidaId").is(partida.id())),
					new Update()
							.setOnInsert("data", partida.data())
							.setOnInsert("criadoEm", agora),
					Presenca.class);
		}
	}

	public void removerDaPartida(String partidaId) {
		mongo.remove(Query.query(Criteria.where("partidaId").is(partidaId)), Presenca.class);
	}
}
