package br.com.aabb.jogador;

import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.aabb.presenca.Presenca;
import br.com.aabb.whatsapp.NumeroBrasileiro;

/**
 * Cadastro de membros. A skill começa igual à skill base e depois só muda pelas regras do jogo.
 */
@Service
public class JogadorService {

	private final JogadorRepository jogadores;
	private final MongoTemplate mongo;
	private final Clock clock;

	JogadorService(JogadorRepository jogadores, MongoTemplate mongo, Clock clock) {
		this.jogadores = jogadores;
		this.mongo = mongo;
		this.clock = clock;
	}

	public List<Jogador> listar() {
		return jogadores.findAll(Sort.by("apelido"));
	}

	public Jogador buscar(String id) {
		return jogadores.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jogador não encontrado"));
	}

	public Jogador criar(JogadorRequest request) {
		double skill = request.skill() != null ? request.skill() : request.skillBase();
		var jogador = salvar(new Jogador(null, request.nome().trim(), request.apelido().trim(), request.whatsapp(),
				List.of(), request.posicoes(), request.skillBase(), skill, request.ehAdmin(), true, Map.of(),
				clock.instant()));
		if (request.presencasNoAno() != null) {
			definirPresencasNoAno(jogador.id(), request.presencasNoAno());
		}
		return buscar(jogador.id());
	}

	/**
	 * Atualiza o cadastro com updates pontuais (sem sobrescrever a skill que o VAR possa ter alterado no meio
	 * tempo). Skill e presenças só mudam se vierem na requisição.
	 */
	public Jogador atualizar(String id, JogadorRequest request) {
		var atual = buscar(id);
		verificarNumeroDisponivel(request.whatsapp(), id);
		var update = new Update()
				.set("nome", request.nome().trim())
				.set("apelido", request.apelido().trim())
				.set("whatsapp", request.whatsapp())
				.set("posicoes", request.posicoes())
				.set("skillBase", request.skillBase())
				.set("admin", request.ehAdmin());
		try {
			mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), update, Jogador.class);
		} catch (DuplicateKeyException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe jogador com esse WhatsApp");
		}
		if (request.skill() != null) {
			definirSkill(atual, request.skill());
		}
		if (request.presencasNoAno() != null) {
			definirPresencasNoAno(id, request.presencasNoAno());
		}
		return buscar(id);
	}

	/** Ajuste manual da skill, registrado no histórico como AJUSTE_MANUAL. */
	private void definirSkill(Jogador atual, double skill) {
		double nova = Math.round(skill * 100) / 100.0;
		double delta = Math.round((nova - buscar(atual.id()).skill()) * 100) / 100.0;
		if (delta == 0) {
			return;
		}
		mongo.updateFirst(Query.query(Criteria.where("_id").is(atual.id())), Update.update("skill", nova),
				Jogador.class);
		mongo.insert(new HistoricoSkill(null, atual.id(), delta, "AJUSTE_MANUAL", atual.id(), clock.instant()));
	}

	/**
	 * O admin informa o total do ano; a diferença para as presenças computadas nos sorteios vira o ajuste
	 * manual daquele ano.
	 */
	private void definirPresencasNoAno(String id, int total) {
		int ano = Year.now(clock).getValue();
		long computadas = mongo.count(Query.query(Criteria.where("jogadorId").is(id)
				.and("data").regex("^" + ano + "-")), Presenca.class);
		int ajuste = (int) (total - computadas);
		var update = ajuste == 0
				? new Update().unset("ajustesDePresenca." + ano)
				: new Update().set("ajustesDePresenca." + ano, ajuste);
		mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), update, Jogador.class);
	}

	/**
	 * Ativa ou desativa o membro. Desativado não é reconhecido nos comandos nem recadastrado automaticamente.
	 */
	public Jogador definirAtivo(String id, boolean ativo) {
		buscar(id);
		mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), Update.update("ativo", ativo), Jogador.class);
		return buscar(id);
	}

	public Jogador desvincularLid(String id, String lid) {
		buscar(id);
		mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().pull("lids", lid), Jogador.class);
		return buscar(id);
	}

	private void verificarNumeroDisponivel(String whatsapp, String id) {
		boolean outroComMesmoNumero = jogadores.findByWhatsappIn(NumeroBrasileiro.variantes(whatsapp)).stream()
				.anyMatch(existente -> !existente.id().equals(id));
		if (outroComMesmoNumero) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe jogador com esse WhatsApp");
		}
	}

	private Jogador salvar(Jogador jogador) {
		verificarNumeroDisponivel(jogador.whatsapp(), jogador.id());
		try {
			return jogadores.save(jogador);
		} catch (DuplicateKeyException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe jogador com esse WhatsApp");
		}
	}
}
