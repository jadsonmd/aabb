package br.com.aabb.painel;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.JogadorRepository;
import br.com.aabb.lance.Lance;
import br.com.aabb.lance.StatusLance;
import br.com.aabb.lance.TipoLance;
import br.com.aabb.painel.PainelDtos.DetalheDaPartida;
import br.com.aabb.painel.PainelDtos.FinalizarPartida;
import br.com.aabb.painel.PainelDtos.LinhaDoRanking;
import br.com.aabb.painel.PainelDtos.PresencasDoJogador;
import br.com.aabb.painel.PainelDtos.ResumoDaPartida;
import br.com.aabb.partida.EventoDaPartida;
import br.com.aabb.partida.Partida;
import br.com.aabb.partida.PartidaService;
import br.com.aabb.partida.Resultado;
import br.com.aabb.presenca.Presenca;

/**
 * Consultas do painel. Os volumes são pequenos (um grupo de pelada), então as agregações são feitas em memória.
 */
@Service
class PainelService {

	private final MongoTemplate mongo;
	private final JogadorRepository jogadores;
	private final PartidaService partidas;
	private final Clock clock;

	PainelService(MongoTemplate mongo, JogadorRepository jogadores, PartidaService partidas, Clock clock) {
		this.mongo = mongo;
		this.jogadores = jogadores;
		this.partidas = partidas;
		this.clock = clock;
	}

	List<LinhaDoRanking> ranking(int ano) {
		List<Partida> doAno = partidas.doAno(ano);
		var idsDasPartidas = doAno.stream().map(Partida::id).toList();
		List<Lance> aprovados = mongo.find(Query.query(Criteria.where("status").is(StatusLance.APPROVED)
				.and("aprovadoEm").gte(inicioDoAno(ano)).lt(inicioDoAno(ano + 1))), Lance.class);

		Map<String, Integer> gols = new HashMap<>();
		Map<String, Integer> canetas = new HashMap<>();
		Map<String, Integer> chapeus = new HashMap<>();
		for (Lance lance : aprovados) {
			if (lance.autor() == null || !lance.autor().identificado()) {
				continue;
			}
			String id = lance.autor().jogadorId();
			switch (lance.tipo()) {
				case GOL -> gols.merge(id, lance.quantidade(), Integer::sum);
				case CANETA -> canetas.merge(id, Math.max(1, lance.quantidade()), Integer::sum);
				case CHAPEU -> chapeus.merge(id, Math.max(1, lance.quantidade()), Integer::sum);
			}
		}

		Map<String, Integer> titulos = new HashMap<>();
		for (Partida partida : doAno) {
			if (partida.resultado() == null || partida.resultado() == Resultado.EMPATE) {
				continue;
			}
			partida.sorteioVigente().ifPresent(s -> (partida.resultado() == Resultado.AZUL ? s.azul() : s.amarelo())
					.membros().forEach(id -> titulos.merge(id, 1, Integer::sum)));
		}

		Map<String, Long> presencas = idsDasPartidas.isEmpty() ? Map.of()
				: mongo.find(Query.query(Criteria.where("partidaId").in(idsDasPartidas)), Presenca.class).stream()
						.collect(Collectors.groupingBy(Presenca::jogadorId, Collectors.counting()));

		// Só aparece quem teve alguma atividade no ano (quem só está no grupo e não joga fica de fora).
		return jogadores.findAll().stream()
				.filter(Jogador::ativo)
				.map(j -> new LinhaDoRanking(j.id(), j.nome(), j.apelido(), j.skill(),
						gols.getOrDefault(j.id(), 0), canetas.getOrDefault(j.id(), 0), chapeus.getOrDefault(j.id(), 0),
						titulos.getOrDefault(j.id(), 0),
						Math.max(0, presencas.getOrDefault(j.id(), 0L).intValue() + j.ajusteDePresenca(ano))))
				.filter(l -> l.presencas() + l.gols() + l.canetas() + l.chapeus() + l.titulos() > 0)
				.sorted(Comparator.comparingDouble(LinhaDoRanking::skill).reversed()
						.thenComparing(LinhaDoRanking::apelido))
				.toList();
	}

	private Instant inicioDoAno(int ano) {
		return LocalDate.of(ano, 1, 1).atStartOfDay(clock.getZone()).toInstant();
	}

	List<ResumoDaPartida> partidasRecentes() {
		return partidas.recentes(60).stream().map(ResumoDaPartida::de).toList();
	}

	DetalheDaPartida partida(String id) {
		Partida partida = partidas.porId(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida não encontrada"));
		List<Lance> gols = mongo.find(Query.query(Criteria.where("tipo").is(TipoLance.GOL)
				.and("partidaId").is(id).and("status").is(StatusLance.APPROVED)), Lance.class);
		List<String> presentes = mongo.find(Query.query(Criteria.where("partidaId").is(id)), Presenca.class).stream()
				.map(Presenca::jogadorId)
				.toList();
		return new DetalheDaPartida(partida, gols, presentes);
	}

	Partida finalizar(String id, FinalizarPartida request) {
		var eventos = request.eventos().stream()
				.map(e -> new EventoDaPartida(e.tipo(), e.jogadorId(), e.nome()))
				.toList();
		return partidas.finalizar(id, request.placarAzul(), request.placarAmarelo(), eventos)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
						"A partida precisa estar sorteada para ser finalizada"));
	}

	/** Quem já jogou ao menos uma vez (presença no sorteio ou ajuste manual), da maior para a menor skill. */
	List<Jogador> rankingDeHabilidade() {
		var jogaram = mongo.findDistinct(new Query(), "jogadorId", Presenca.class, String.class);
		return jogadores.findAll().stream()
				.filter(Jogador::ativo)
				.filter(j -> jogaram.contains(j.id()) || j.ajusteDePresencaTotal() > 0)
				.sorted(Comparator.comparingDouble(Jogador::skill).reversed().thenComparing(Jogador::apelido))
				.toList();
	}

	List<PresencasDoJogador> presencas(int ano) {
		var todas = mongo.findAll(Presenca.class).stream()
				.collect(Collectors.groupingBy(Presenca::jogadorId));
		String prefixo = ano + "-";
		return jogadores.findAll().stream()
				.filter(Jogador::ativo)
				.map(j -> {
					List<String> datas = todas.getOrDefault(j.id(), List.of()).stream()
							.map(Presenca::data)
							.sorted(Comparator.reverseOrder())
							.toList();
					int noAno = (int) datas.stream().filter(d -> d.startsWith(prefixo)).count() + j.ajusteDePresenca(ano);
					int total = datas.size() + j.ajusteDePresencaTotal();
					return new PresencasDoJogador(j.id(), j.apelido(), Math.max(0, noAno), Math.max(0, total), datas);
				})
				.sorted(Comparator.comparingInt(PresencasDoJogador::noAno).reversed()
						.thenComparing(PresencasDoJogador::apelido))
				.toList();
	}
}
