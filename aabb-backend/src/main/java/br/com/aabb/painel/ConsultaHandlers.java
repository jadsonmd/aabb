package br.com.aabb.painel;

import java.time.Clock;
import java.time.Year;
import java.util.Comparator;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.aabb.comando.Comando;
import br.com.aabb.comando.ComandoHandler;
import br.com.aabb.comando.Contexto;
import br.com.aabb.comando.Mensagens;
import br.com.aabb.jogador.Jogador;
import br.com.aabb.painel.PainelDtos.LinhaDoRanking;
import br.com.aabb.painel.PainelDtos.PresencasDoJogador;
import br.com.aabb.whatsapp.EnviadorWhatsApp;

/** {@code #ranking}: skill de quem já jogou, da maior para a menor. Funciona em qualquer dia. */
@Component
class RankingHandler implements ComandoHandler {

	private final PainelService painel;
	private final EnviadorWhatsApp enviador;

	RankingHandler(PainelService painel, EnviadorWhatsApp enviador) {
		this.painel = painel;
		this.enviador = enviador;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.RANKING);
	}

	@Override
	public void executar(Contexto contexto) {
		var jogadores = painel.rankingDeHabilidade();
		enviador.enviar(Mensagens.classificacao("🏆 Ranking de habilidade",
				jogadores.stream().map(Jogador::nomeExibido).toList(),
				jogadores.stream().map(j -> Mensagens.decimal(j.skill())).toList(),
				"Ninguém jogou ainda."));
	}
}

/** {@code #presenca}: presenças no ano, de quem tem mais para quem tem menos. Funciona em qualquer dia. */
@Component
class PresencaHandler implements ComandoHandler {

	private final PainelService painel;
	private final EnviadorWhatsApp enviador;
	private final Clock clock;

	PresencaHandler(PainelService painel, EnviadorWhatsApp enviador, Clock clock) {
		this.painel = painel;
		this.enviador = enviador;
		this.clock = clock;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.PRESENCA);
	}

	@Override
	public void executar(Contexto contexto) {
		int ano = Year.now(clock).getValue();
		var presencas = painel.presencas(ano).stream().filter(p -> p.noAno() > 0).toList();
		enviador.enviar(Mensagens.classificacao("📋 Presenças em " + ano,
				presencas.stream().map(PresencasDoJogador::apelido).toList(),
				presencas.stream().map(p -> String.valueOf(p.noAno())).toList(),
				"Nenhuma presença registrada este ano."));
	}
}

/** {@code #titulos}: vitórias no ano, de quem tem mais para quem tem menos. Funciona em qualquer dia. */
@Component
class TitulosHandler implements ComandoHandler {

	private final PainelService painel;
	private final EnviadorWhatsApp enviador;
	private final Clock clock;

	TitulosHandler(PainelService painel, EnviadorWhatsApp enviador, Clock clock) {
		this.painel = painel;
		this.enviador = enviador;
		this.clock = clock;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.TITULOS);
	}

	@Override
	public void executar(Contexto contexto) {
		int ano = Year.now(clock).getValue();
		var campeoes = painel.ranking(ano).stream()
				.filter(l -> l.titulos() > 0)
				.sorted(Comparator.comparingInt(LinhaDoRanking::titulos).reversed()
						.thenComparing(LinhaDoRanking::apelido))
				.toList();
		enviador.enviar(Mensagens.classificacao("🏆 Títulos em " + ano,
				campeoes.stream().map(LinhaDoRanking::apelido).toList(),
				campeoes.stream().map(l -> String.valueOf(l.titulos())).toList(),
				"Nenhum título registrado este ano."));
	}
}

