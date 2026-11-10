package br.com.aabb.partida;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.aabb.comando.Mensagens;
import br.com.aabb.whatsapp.EnviadorWhatsApp;

/**
 * Abre a lista às 6h dos dias de jogo. Não há recuperação de execução perdida:
 * se o servidor estiver fora do ar nesse horário, o admin usa {@code #abrirlista}.
 */
@Component
class AberturaDaLista {

	private static final Logger log = LoggerFactory.getLogger(AberturaDaLista.class);

	private final PartidaService partidas;
	private final AgendaService agenda;
	private final EnviadorWhatsApp enviador;

	AberturaDaLista(PartidaService partidas, AgendaService agenda, EnviadorWhatsApp enviador) {
		this.partidas = partidas;
		this.agenda = agenda;
		this.enviador = enviador;
	}

	@Scheduled(cron = "${aabb.agenda.abertura-cron}", zone = "${aabb.agenda.zona}")
	void abrir() {
		if (!agenda.hojeTemJogo()) {
			return;
		}
		var hoje = agenda.hoje();
		if (partidas.abrir(hoje)) {
			log.info("Lista do dia {} aberta pelo scheduler", hoje);
			enviador.enviar(Mensagens.listaAberta(agenda.descricaoDoJogo(hoje)));
		}
	}
}
