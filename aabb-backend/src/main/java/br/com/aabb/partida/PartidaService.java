package br.com.aabb.partida;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import br.com.aabb.sorteio.Sorteio;

/**
 * Operações sobre a partida do dia. Toda regra "verificar e depois alterar" é um update
 * condicional atômico: a condição fica no filtro e o MongoDB garante que só uma escrita concorrente vence.
 */
@Service
public class PartidaService {

	private static final FindAndModifyOptions RETORNAR_NOVO = FindAndModifyOptions.options().returnNew(true);

	private final MongoTemplate mongo;
	private final Clock clock;

	PartidaService(MongoTemplate mongo, Clock clock) {
		this.mongo = mongo;
		this.clock = clock;
	}

	public Optional<Partida> doDia(LocalDate dia) {
		return Optional.ofNullable(mongo.findOne(porData(dia), Partida.class));
	}

	public Optional<Partida> porId(String id) {
		return Optional.ofNullable(mongo.findById(id, Partida.class));
	}

	/**
	 * Cria a partida do dia, se ainda não existir (idempotente).
	 *
	 * @return {@code true} se a partida foi criada agora
	 */
	public boolean abrir(LocalDate dia) {
		return abrir(dia, null);
	}

	/** Igual a {@link #abrir(LocalDate)}, com horário opcional ({@code null} = horário padrão). */
	public boolean abrir(LocalDate dia, LocalTime horario) {
		var resultado = mongo.upsert(porData(dia),
				new Update()
						.setOnInsert("horario", horario == null ? null : horario.toString())
						.setOnInsert("status", StatusPartida.ABERTA)
						.setOnInsert("confirmados", List.of())
						.setOnInsert("sorteios", List.of())
						.setOnInsert("sorteioAtual", 0)
						.setOnInsert("criadaEm", clock.instant()),
				Partida.class);
		return resultado.getUpsertedId() != null;
	}

	/** Muda o horário do jogo do dia (se não estiver cancelado). */
	public Optional<Partida> alterarHorario(LocalDate dia, LocalTime horario) {
		var filtro = porData(dia).addCriteria(Criteria.where("status").ne(StatusPartida.CANCELADA));
		return modificar(filtro, new Update().set("horario", horario.toString()));
	}

	/** Inclui um membro no fim da lista, se a lista estiver aberta e ele ainda não estiver nela. */
	public Optional<Partida> confirmarMembro(String partidaId, Confirmado membro) {
		var filtro = aberta(partidaId).addCriteria(Criteria.where("confirmados.jogadorId").ne(membro.jogadorId()));
		return modificar(filtro, new Update().push("confirmados", membro));
	}

	/** Inclui um convidado, se a lista estiver aberta e não houver convidado com o mesmo nome. */
	public Optional<Partida> adicionarConvidado(String partidaId, Confirmado convidado) {
		var filtro = aberta(partidaId).addCriteria(Criteria.where("confirmados").not().elemMatch(
				Criteria.where("tipo").is(TipoConfirmado.CONVIDADO).and("nomeNormalizado").is(convidado.nomeNormalizado())));
		return modificar(filtro, new Update().push("confirmados", convidado));
	}

	public Optional<Partida> removerConvidado(String partidaId, String nomeNormalizado) {
		var convidado = Criteria.where("tipo").is(TipoConfirmado.CONVIDADO.name()).and("nomeNormalizado").is(nomeNormalizado);
		var filtro = aberta(partidaId).addCriteria(Criteria.where("confirmados").elemMatch(convidado));
		var update = new Update().pull("confirmados",
				Query.query(convidado).getQueryObject());
		return modificar(filtro, update);
	}

	public Optional<Partida> removerMembro(String partidaId, String jogadorId) {
		var membro = Criteria.where("tipo").is(TipoConfirmado.MEMBRO.name()).and("jogadorId").is(jogadorId);
		var filtro = aberta(partidaId).addCriteria(Criteria.where("confirmados").elemMatch(membro));
		return modificar(filtro, new Update().pull("confirmados", Query.query(membro).getQueryObject()));
	}

	/** Fecha a lista para o sorteio: exige lista aberta e o mínimo de confirmados. */
	public Optional<Partida> fecharParaSorteio(String partidaId, int minimo) {
		var filtro = aberta(partidaId).addCriteria(Criteria.expr(
				ComparisonOperators.Gte.valueOf(ArrayOperators.Size.lengthOfArray("confirmados"))
						.greaterThanEqualToValue(minimo)));
		return modificar(filtro, new Update().set("status", StatusPartida.SORTEADA));
	}

	/**
	 * Grava um novo sorteio, desde que a versão vigente ainda seja a lida e o resultado não tenha sido
	 * registrado. Se dois sorteios concorrerem, só um é gravado.
	 */
	public Optional<Partida> gravarSorteio(String partidaId, Sorteio sorteio, int versaoLida) {
		var filtro = Query.query(Criteria.where("_id").is(partidaId)
				.and("status").is(StatusPartida.SORTEADA)
				.and("sorteioAtual").is(versaoLida)
				.and("resultado").is(null));
		return modificar(filtro, new Update().push("sorteios", sorteio).set("sorteioAtual", sorteio.versao()));
	}

	/** Cancela a partida (aberta ou sorteada, sem resultado). O cancelamento é definitivo. */
	public Optional<Partida> cancelar(String partidaId) {
		var filtro = Query.query(Criteria.where("_id").is(partidaId)
				.and("status").in(StatusPartida.ABERTA, StatusPartida.SORTEADA)
				.and("resultado").is(null));
		return modificar(filtro, new Update().set("status", StatusPartida.CANCELADA));
	}

	/** Registra o resultado uma única vez (o primeiro comando vence). */
	public Optional<Partida> registrarResultado(String partidaId, Resultado resultado, String informadoPor) {
		var filtro = Query.query(Criteria.where("_id").is(partidaId)
				.and("status").in(StatusPartida.SORTEADA, StatusPartida.FINALIZADA)
				.and("sorteioAtual").gt(0)
				.and("resultado").is(null));
		return modificar(filtro, new Update()
				.set("resultado", resultado)
				.set("resultadoInformadoPor", informadoPor)
				.set("resultadoEm", clock.instant()));
	}

	/** Lança placar e eventos do pós-jogo e finaliza a partida (sorteada ou já finalizada). */
	public Optional<Partida> finalizar(String partidaId, int placarAzul, int placarAmarelo,
			List<EventoDaPartida> eventos) {
		var filtro = Query.query(Criteria.where("_id").is(partidaId)
				.and("status").in(StatusPartida.SORTEADA, StatusPartida.FINALIZADA)
				.and("sorteioAtual").gt(0));
		return modificar(filtro, new Update()
				.set("status", StatusPartida.FINALIZADA)
				.set("placarAzul", placarAzul)
				.set("placarAmarelo", placarAmarelo)
				.set("eventos", eventos)
				.set("finalizadaEm", clock.instant()));
	}

	public List<Partida> recentes(int limite) {
		return mongo.find(new Query().with(Sort.by("data").descending()).limit(limite),
				Partida.class);
	}

	public List<Partida> doAno(int ano) {
		return mongo.find(Query.query(Criteria.where("data").gte(ano + "-01-01").lte(ano + "-12-31")), Partida.class);
	}

	private Optional<Partida> modificar(Query filtro, Update update) {
		return Optional.ofNullable(mongo.findAndModify(filtro, update, RETORNAR_NOVO, Partida.class));
	}

	private static Query porData(LocalDate dia) {
		return Query.query(Criteria.where("data").is(dia.toString()));
	}

	private static Query aberta(String partidaId) {
		return Query.query(Criteria.where("_id").is(partidaId).and("status").is(StatusPartida.ABERTA));
	}
}
