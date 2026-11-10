package br.com.aabb.sorteio;

import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.aabb.comando.Comando;
import br.com.aabb.comando.ComandoHandler;
import br.com.aabb.comando.Contexto;
import br.com.aabb.comando.Mensagens;
import br.com.aabb.config.AabbProperties;
import br.com.aabb.partida.AgendaService;
import br.com.aabb.partida.Partida;
import br.com.aabb.partida.PartidaDoDia;
import br.com.aabb.partida.PartidaService;
import br.com.aabb.partida.StatusPartida;
import br.com.aabb.presenca.PresencaService;
import br.com.aabb.whatsapp.EnviadorWhatsApp;

/** Publica o sorteio e registra a presença dos membros sorteados. */
@Component
class PublicadorDeSorteio {

	private final PresencaService presencas;
	private final AgendaService agenda;
	private final EnviadorWhatsApp enviador;

	PublicadorDeSorteio(PresencaService presencas, AgendaService agenda, EnviadorWhatsApp enviador) {
		this.presencas = presencas;
		this.agenda = agenda;
		this.enviador = enviador;
	}

	void publicar(Partida partida) {
		var sorteio = partida.sorteioVigente().orElseThrow();
		// Idempotente: reprocessar ou ressortear não duplica presença.
		presencas.registrar(partida, sorteio.membros());
		enviador.enviar(Mensagens.times(agenda.descricaoDoJogo(partida), sorteio));
	}
}

/** {@code #sortear}: fecha a lista (mínimo de 10) e sorteia os times. */
@Component
class SortearHandler implements ComandoHandler {

	private final PartidaDoDia partidaDoDia;
	private final PartidaService partidas;
	private final SorteioService sorteios;
	private final PublicadorDeSorteio publicador;
	private final EnviadorWhatsApp enviador;
	private final int minimo;

	SortearHandler(PartidaDoDia partidaDoDia, PartidaService partidas, SorteioService sorteios,
			PublicadorDeSorteio publicador, EnviadorWhatsApp enviador, AabbProperties properties) {
		this.partidaDoDia = partidaDoDia;
		this.partidas = partidas;
		this.sorteios = sorteios;
		this.publicador = publicador;
		this.enviador = enviador;
		this.minimo = properties.sorteio().minimoJogadores();
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.SORTEAR);
	}

	@Override
	public void executar(Contexto contexto) {
		var partida = partidaDoDia.obter(contexto).orElse(null);
		if (partida == null) {
			return;
		}
		if (partida.status() != StatusPartida.ABERTA) {
			enviador.enviar(Mensagens.JA_SORTEADO_USE_RESSORTEAR);
			return;
		}
		var fechada = partidas.fecharParaSorteio(partida.id(), minimo).orElse(null);
		if (fechada == null) {
			var atual = partidas.porId(partida.id()).orElseThrow();
			enviador.enviar(atual.status() == StatusPartida.ABERTA
					? Mensagens.minimoParaSortear(atual.confirmados().size(), minimo)
					: Mensagens.JA_SORTEADO_USE_RESSORTEAR);
			return;
		}
		sorteios.sortear(fechada).ifPresent(publicador::publicar);
	}
}

/** {@code #ressortear}: novos times com a mesma lista, enquanto o resultado não foi registrado. */
@Component
class RessortearHandler implements ComandoHandler {

	private final PartidaDoDia partidaDoDia;
	private final SorteioService sorteios;
	private final PublicadorDeSorteio publicador;
	private final EnviadorWhatsApp enviador;

	RessortearHandler(PartidaDoDia partidaDoDia, SorteioService sorteios, PublicadorDeSorteio publicador,
			EnviadorWhatsApp enviador) {
		this.partidaDoDia = partidaDoDia;
		this.sorteios = sorteios;
		this.publicador = publicador;
		this.enviador = enviador;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.RESSORTEAR);
	}

	@Override
	public void executar(Contexto contexto) {
		var partida = partidaDoDia.obter(contexto).orElse(null);
		if (partida == null) {
			return;
		}
		if (partida.status() == StatusPartida.ABERTA) {
			enviador.enviar(Mensagens.SEM_SORTEIO_USE_SORTEAR);
			return;
		}
		if (partida.status() == StatusPartida.FINALIZADA || partida.temResultado()) {
			enviador.enviar(Mensagens.RESULTADO_TRAVA_TIMES);
			return;
		}
		sorteios.sortear(partida).ifPresent(publicador::publicar);
	}
}
