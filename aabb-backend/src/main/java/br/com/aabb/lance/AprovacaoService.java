package br.com.aabb.lance;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aabb.config.AabbProperties;
import br.com.aabb.jogador.SkillService;

/**
 * Aprovação de lances pelo VAR: a mudança de status e a skill são gravadas na mesma transação.
 */
@Service
class AprovacaoService {

	private final MongoTemplate mongo;
	private final SkillService skill;
	private final AabbProperties.Skill config;
	private final Clock clock;

	AprovacaoService(MongoTemplate mongo, SkillService skill, AabbProperties properties, Clock clock) {
		this.mongo = mongo;
		this.skill = skill;
		this.config = properties.skill();
		this.clock = clock;
	}

	/**
	 * Aprova o lance se ele estiver pendente e tiver votos suficientes.
	 *
	 * @return o lance aprovado, ou vazio se não houve aprovação agora
	 * @throws org.springframework.dao.DuplicateKeyException se já existir gol aprovado do jogador na partida
	 */
	@Transactional
	public Optional<Lance> aprovarSePossivel(String messageId) {
		var filtro = Query.query(Criteria.where("messageId").is(messageId)
				.and("status").is(StatusLance.PENDING)
				.andOperator(Criteria.expr(ComparisonOperators.Gte
						.valueOf(ArrayOperators.Size.lengthOfArray("approvers"))
						.greaterThanEqualToValue(config.votosNecessarios()))));
		Lance aprovado = mongo.findAndModify(filtro,
				new Update().set("status", StatusLance.APPROVED).set("aprovadoEm", clock.instant()),
				FindAndModifyOptions.options().returnNew(true), Lance.class);
		if (aprovado == null) {
			return Optional.empty();
		}
		switch (aprovado.tipo()) {
			case CANETA, CHAPEU -> {
				// Lances antigos (antes da quantidade) têm quantidade 0: valem 1.
				double peso = config.jogadaEfeito() * Math.max(1, aprovado.quantidade());
				ajustar(aprovado.autor(), peso, aprovado);
				ajustar(aprovado.vitima(), -peso, aprovado);
			}
			case GOL -> {
				// Os demais #marcou pendentes do mesmo jogador na partida perdem a validade.
				mongo.updateMulti(Query.query(Criteria.where("tipo").is(TipoLance.GOL)
						.and("partidaId").is(aprovado.partidaId())
						.and("autor.jogadorId").is(aprovado.autor().jogadorId())
						.and("status").is(StatusLance.PENDING)),
						new Update().set("status", StatusLance.DESCARTADO), Lance.class);
				ajustar(aprovado.autor(), config.gol() * aprovado.quantidade(), aprovado);
			}
		}
		return Optional.of(aprovado);
	}

	private void ajustar(Lado lado, double delta, Lance lance) {
		if (lado != null && lado.identificado()) {
			skill.ajustar(List.of(lado.jogadorId()), delta, lance.tipo().name(), lance.id());
		}
	}

	static List<String> identificados(Lance lance) {
		return Stream.of(lance.autor(), lance.vitima())
				.filter(l -> l != null && l.identificado())
				.map(Lado::jogadorId)
				.toList();
	}
}
