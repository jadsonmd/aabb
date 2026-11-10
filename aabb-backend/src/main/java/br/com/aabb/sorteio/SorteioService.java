package br.com.aabb.sorteio;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import br.com.aabb.config.AabbProperties;
import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.JogadorRepository;
import br.com.aabb.partida.Confirmado;
import br.com.aabb.partida.Partida;
import br.com.aabb.partida.PartidaService;

/**
 * Monta a entrada do algoritmo a partir da lista do dia, sorteia e grava o resultado na partida.
 */
@Service
public class SorteioService {

	private final JogadorRepository jogadores;
	private final PartidaService partidas;
	private final AabbProperties.Sorteio config;
	private final AlgoritmoSorteio algoritmo;
	private final Clock clock;
	private final SecureRandom sementes = new SecureRandom();

	SorteioService(JogadorRepository jogadores, PartidaService partidas, AabbProperties properties, Clock clock) {
		this.jogadores = jogadores;
		this.partidas = partidas;
		this.config = properties.sorteio();
		this.algoritmo = new AlgoritmoSorteio(config.toleranciaSkill());
		this.clock = clock;
	}

	/**
	 * Sorteia novos times para uma partida já fechada ({@code SORTEADA}) e grava como próxima versão.
	 *
	 * @return a partida atualizada, ou vazio se outro sorteio foi gravado ao mesmo tempo
	 */
	public Optional<Partida> sortear(Partida partida) {
		List<Confirmado> fila = filaDePrioridade(partida.confirmados());
		int titulares = Math.min(config.titulares(), fila.size());
		Map<String, Jogador> cadastro = jogadores.findAllById(
				fila.stream().filter(Confirmado::membro).map(Confirmado::jogadorId).toList())
				.stream().collect(Collectors.toMap(Jogador::id, Function.identity()));

		List<Participante> participantes = fila.stream().map(c -> participante(c, cadastro)).toList();
		long seed = sementes.nextLong();
		var usadas = partida.sorteios().stream().map(Sorteio::composicao).collect(Collectors.toSet());
		var resultado = algoritmo.sortear(
				participantes.subList(0, titulares),
				participantes.subList(titulares, participantes.size()),
				seed, usadas);

		var sorteio = new Sorteio(partida.sorteioAtual() + 1, seed, AlgoritmoSorteio.VERSAO,
				resultado.azul(), resultado.amarelo(), resultado.composicao(), clock.instant());
		return partidas.gravarSorteio(partida.id(), sorteio, partida.sorteioAtual());
	}

	/** Membros têm prioridade sobre convidados; cada grupo em ordem de confirmação. */
	static List<Confirmado> filaDePrioridade(List<Confirmado> confirmados) {
		return Stream.concat(
				confirmados.stream().filter(Confirmado::membro),
				confirmados.stream().filter(c -> !c.membro()))
				.toList();
	}

	private static Participante participante(Confirmado confirmado, Map<String, Jogador> cadastro) {
		if (!confirmado.membro()) {
			return new Participante(confirmado.tipo(), "c:" + confirmado.nomeNormalizado(), null,
					confirmado.nome(), 0, null);
		}
		Jogador jogador = cadastro.get(confirmado.jogadorId());
		return new Participante(confirmado.tipo(), confirmado.jogadorId(), confirmado.jogadorId(), confirmado.nome(),
				jogador == null ? 0 : jogador.skill(),
				jogador == null ? null : jogador.posicaoPrincipal());
	}
}
