package br.com.aabb.comando;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.aabb.jogador.CadastroAutomaticoService;
import br.com.aabb.lance.VarService;
import br.com.aabb.partida.AgendaService;
import br.com.aabb.partida.PartidaService;
import br.com.aabb.whatsapp.EnviadorWhatsApp;
import br.com.aabb.whatsapp.MensagemRecebida;
import br.com.aabb.whatsapp.ProcessadorDeMensagem;
import br.com.aabb.whatsapp.ResolvedorDeParticipante;

/**
 * Aplica as regras gerais (dia de jogo, identificação, admin) e repassa ao handler da hashtag.
 * Reações vão para o VAR.
 */
@Component
class RoteadorDeComandos implements ProcessadorDeMensagem {

	private static final Logger log = LoggerFactory.getLogger(RoteadorDeComandos.class);

	private final Map<Comando, ComandoHandler> handlers = new EnumMap<>(Comando.class);
	private final ResolvedorDeParticipante resolvedor;
	private final CadastroAutomaticoService cadastroAutomatico;
	private final AgendaService agenda;
	private final PartidaService partidas;
	private final VarService var;
	private final EnviadorWhatsApp enviador;

	RoteadorDeComandos(List<ComandoHandler> handlers, ResolvedorDeParticipante resolvedor,
			CadastroAutomaticoService cadastroAutomatico, AgendaService agenda, PartidaService partidas, VarService var,
			EnviadorWhatsApp enviador) {
		handlers.forEach(h -> h.comandos().forEach(c -> this.handlers.put(c, h)));
		this.resolvedor = resolvedor;
		this.cadastroAutomatico = cadastroAutomatico;
		this.agenda = agenda;
		this.partidas = partidas;
		this.var = var;
		this.enviador = enviador;
	}

	/**
	 * Comandos de lista valem nos dias de jogo programados e em qualquer dia em que o admin tenha aberto
	 * um jogo extra. O {@code #abrirlista} vale sempre (é ele que abre o jogo extra).
	 */
	private boolean diaComJogo(Comando comando) {
		return comando == Comando.ABRIRLISTA || agenda.hojeTemJogo() || partidas.doDia(agenda.hoje()).isPresent();
	}

	@Override
	public void processar(MensagemRecebida mensagem) {
		// Resolver toda mensagem também aprende vínculos LID ↔ número.
		var remetente = resolvedor.resolver(mensagem.remetente());
		if (remetente.isEmpty()) {
			remetente = cadastroAutomatico.cadastrarSeNovo(mensagem.remetente(), mensagem.nomeExibido());
		}

		if (mensagem.isReacao()) {
			remetente.ifPresent(votante -> var.votar(mensagem.reacao(), votante));
			return;
		}

		var recebido = ComandoRecebido.extrair(mensagem.texto()).orElse(null);
		if (recebido == null) {
			return;
		}
		Comando comando = recebido.comando();
		if (comando.soDiaDeJogo() && !diaComJogo(comando)) {
			return;
		}
		if (comando.exigeIdentificacao() && remetente.isEmpty()) {
			enviador.enviar(Mensagens.NAO_RECONHECIDO);
			return;
		}
		if (comando.admin() && remetente.map(j -> !j.admin()).orElse(true)) {
			enviador.enviar(Mensagens.SO_ADMIN);
			return;
		}
		var handler = handlers.get(comando);
		if (handler == null) {
			log.warn("Comando #{} sem handler", comando.hashtag());
			return;
		}
		log.info("#{} de {}", comando.hashtag(), remetente.map(j -> j.nomeExibido()).orElse(mensagem.nomeExibido()));
		handler.executar(new Contexto(mensagem, recebido, remetente, agenda.hoje()));
	}
}
