package br.com.aabb.partida;

import java.time.Clock;
import java.time.LocalTime;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.aabb.comando.Comando;
import br.com.aabb.comando.ComandoHandler;
import br.com.aabb.comando.Contexto;
import br.com.aabb.comando.Mensagens;
import br.com.aabb.presenca.PresencaService;
import br.com.aabb.whatsapp.EnviadorWhatsApp;

/** {@code #vou}: o membro entra no fim da lista. */
@Component
class VouHandler implements ComandoHandler {

	private final PartidaDoDia partidaDoDia;
	private final PartidaService partidas;
	private final AgendaService agenda;
	private final EnviadorWhatsApp enviador;
	private final Clock clock;

	VouHandler(PartidaDoDia partidaDoDia, PartidaService partidas, AgendaService agenda, EnviadorWhatsApp enviador,
			Clock clock) {
		this.partidaDoDia = partidaDoDia;
		this.partidas = partidas;
		this.agenda = agenda;
		this.enviador = enviador;
		this.clock = clock;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.VOU);
	}

	@Override
	public void executar(Contexto contexto) {
		var partida = partidaDoDia.obter(contexto).orElse(null);
		if (partida == null) {
			return;
		}
		var membro = contexto.membro();
		partidas.confirmarMembro(partida.id(), Confirmado.membro(membro.id(), membro.nomeExibido(), clock.instant()))
				.ifPresentOrElse(
						atualizada -> enviador.enviar(Mensagens.lista(Mensagens.confirmado(membro.nomeExibido()),
								agenda.descricaoDoJogo(atualizada), atualizada.confirmados())),
						() -> enviador.enviar(partidas.porId(partida.id()).orElseThrow().status() == StatusPartida.ABERTA
								? Mensagens.jaNaLista(membro.nomeExibido())
								: Mensagens.JA_SORTEADO));
	}
}

/** {@code #convidado <nome>}: qualquer membro coloca um convidado na lista. */
@Component
class ConvidadoHandler implements ComandoHandler {

	private static final int TAMANHO_MAXIMO_NOME = 40;

	private final PartidaDoDia partidaDoDia;
	private final PartidaService partidas;
	private final AgendaService agenda;
	private final EnviadorWhatsApp enviador;
	private final Clock clock;

	ConvidadoHandler(PartidaDoDia partidaDoDia, PartidaService partidas, AgendaService agenda,
			EnviadorWhatsApp enviador, Clock clock) {
		this.partidaDoDia = partidaDoDia;
		this.partidas = partidas;
		this.agenda = agenda;
		this.enviador = enviador;
		this.clock = clock;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.CONVIDADO);
	}

	@Override
	public void executar(Contexto contexto) {
		String nome = Nomes.limpar(contexto.comando().depois());
		if (nome.isEmpty() || nome.length() > TAMANHO_MAXIMO_NOME) {
			enviador.enviar(Mensagens.usoConvidado());
			return;
		}
		var partida = partidaDoDia.obter(contexto).orElse(null);
		if (partida == null) {
			return;
		}
		var membro = contexto.membro();
		var convidado = Confirmado.convidado(nome, membro.id(), membro.nomeExibido(), clock.instant());
		partidas.adicionarConvidado(partida.id(), convidado)
				.ifPresentOrElse(
						atualizada -> enviador.enviar(Mensagens.lista(Mensagens.convidadoEntrou(convidado),
								agenda.descricaoDoJogo(atualizada), atualizada.confirmados())),
						() -> enviador.enviar(partidas.porId(partida.id()).orElseThrow().status() == StatusPartida.ABERTA
								? Mensagens.jaNaLista(nome)
								: Mensagens.JA_SORTEADO));
	}
}

/** {@code #desconvidar <nome>}: qualquer membro remove um convidado. */
@Component
class DesconvidarHandler implements ComandoHandler {

	private final PartidaDoDia partidaDoDia;
	private final PartidaService partidas;
	private final EnviadorWhatsApp enviador;

	DesconvidarHandler(PartidaDoDia partidaDoDia, PartidaService partidas, EnviadorWhatsApp enviador) {
		this.partidaDoDia = partidaDoDia;
		this.partidas = partidas;
		this.enviador = enviador;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.DESCONVIDAR);
	}

	@Override
	public void executar(Contexto contexto) {
		String nome = Nomes.limpar(contexto.comando().depois());
		if (nome.isEmpty()) {
			enviador.enviar(Mensagens.usoDesconvidar());
			return;
		}
		var partida = partidaDoDia.obter(contexto).orElse(null);
		if (partida == null) {
			return;
		}
		String normalizado = Nomes.normalizar(nome);
		String nomeNaLista = partida.confirmados().stream()
				.filter(c -> normalizado.equals(c.nomeNormalizado()))
				.map(Confirmado::nome)
				.findFirst()
				.orElse(nome);
		partidas.removerConvidado(partida.id(), normalizado)
				.ifPresentOrElse(
						atualizada -> enviador.enviar(
								Mensagens.convidadoRemovido(nomeNaLista, atualizada.confirmados().size())),
						() -> enviador.enviar(partidas.porId(partida.id()).orElseThrow().status() == StatusPartida.ABERTA
								? Mensagens.convidadoNaoEncontrado(nome)
								: Mensagens.JA_SORTEADO));
	}
}

/** {@code #dm}: o próprio membro sai da lista. */
@Component
class DmHandler implements ComandoHandler {

	private final PartidaDoDia partidaDoDia;
	private final PartidaService partidas;
	private final EnviadorWhatsApp enviador;

	DmHandler(PartidaDoDia partidaDoDia, PartidaService partidas, EnviadorWhatsApp enviador) {
		this.partidaDoDia = partidaDoDia;
		this.partidas = partidas;
		this.enviador = enviador;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.DM);
	}

	@Override
	public void executar(Contexto contexto) {
		var partida = partidaDoDia.obter(contexto).orElse(null);
		if (partida == null) {
			return;
		}
		var membro = contexto.membro();
		partidas.removerMembro(partida.id(), membro.id())
				.ifPresentOrElse(
						atualizada -> enviador.enviar(
								Mensagens.saiuDaLista(membro.nomeExibido(), atualizada.confirmados().size())),
						() -> enviador.enviar(partidas.porId(partida.id()).orElseThrow().status() == StatusPartida.ABERTA
								? Mensagens.foraDaLista(membro.nomeExibido())
								: Mensagens.JA_SORTEADO));
	}
}

/** {@code #lista}: confirmados (lista aberta) ou times do sorteio vigente. */
@Component
class ListaHandler implements ComandoHandler {

	private final PartidaDoDia partidaDoDia;
	private final AgendaService agenda;
	private final EnviadorWhatsApp enviador;

	ListaHandler(PartidaDoDia partidaDoDia, AgendaService agenda, EnviadorWhatsApp enviador) {
		this.partidaDoDia = partidaDoDia;
		this.agenda = agenda;
		this.enviador = enviador;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.LISTA);
	}

	@Override
	public void executar(Contexto contexto) {
		var partida = partidaDoDia.obter(contexto).orElse(null);
		if (partida == null) {
			return;
		}
		String descricao = agenda.descricaoDoJogo(partida);
		if (partida.status() == StatusPartida.ABERTA) {
			enviador.enviar(Mensagens.lista(null, descricao, partida.confirmados()));
		} else {
			partida.sorteioVigente().ifPresentOrElse(
					sorteio -> enviador.enviar(Mensagens.times(descricao, sorteio)),
					() -> enviador.enviar(Mensagens.JA_SORTEADO));
		}
	}
}

/**
 * {@code #abrirlista [horário]}: abre o jogo de hoje em qualquer dia (plano B do scheduler das 6h ou jogo extra).
 * Com o jogo já aberto, o horário informado substitui o atual.
 */
@Component
class AbrirListaHandler implements ComandoHandler {

	private final PartidaService partidas;
	private final AgendaService agenda;
	private final EnviadorWhatsApp enviador;

	AbrirListaHandler(PartidaService partidas, AgendaService agenda, EnviadorWhatsApp enviador) {
		this.partidas = partidas;
		this.agenda = agenda;
		this.enviador = enviador;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.ABRIRLISTA);
	}

	@Override
	public void executar(Contexto contexto) {
		String textoHorario = contexto.comando().depois();
		LocalTime horario = null;
		if (!textoHorario.isBlank()) {
			horario = Horario.ler(textoHorario).orElse(null);
			if (horario == null) {
				enviador.enviar(Mensagens.HORARIO_INVALIDO);
				return;
			}
		}
		if (partidas.abrir(contexto.hoje(), horario)) {
			enviador.enviar(Mensagens.listaAberta(agenda.descricaoDoJogo(partidas.doDia(contexto.hoje()).orElseThrow())));
			return;
		}
		// Jogo de hoje já existe: com horário, o admin está mudando a hora do jogo.
		if (horario != null) {
			var alterada = partidas.alterarHorario(contexto.hoje(), horario);
			if (alterada.isPresent()) {
				enviador.enviar(Mensagens.horarioAlterado(agenda.descricaoDoJogo(alterada.get())));
				return;
			}
		}
		enviador.enviar(switch (partidas.doDia(contexto.hoje()).orElseThrow().status()) {
			case ABERTA -> Mensagens.LISTA_JA_ABERTA;
			case SORTEADA, FINALIZADA -> Mensagens.JA_SORTEADO;
			case CANCELADA -> Mensagens.CANCELADO;
		});
	}
}

/** {@code #jogocancelado}: cancelamento definitivo da partida do dia. */
@Component
class JogoCanceladoHandler implements ComandoHandler {

	private final PartidaDoDia partidaDoDia;
	private final PartidaService partidas;
	private final PresencaService presencas;
	private final EnviadorWhatsApp enviador;

	JogoCanceladoHandler(PartidaDoDia partidaDoDia, PartidaService partidas, PresencaService presencas,
			EnviadorWhatsApp enviador) {
		this.partidaDoDia = partidaDoDia;
		this.partidas = partidas;
		this.presencas = presencas;
		this.enviador = enviador;
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.JOGOCANCELADO);
	}

	@Override
	public void executar(Contexto contexto) {
		var partida = partidaDoDia.obter(contexto).orElse(null);
		if (partida == null) {
			return;
		}
		if (partidas.cancelar(partida.id()).isPresent()) {
			presencas.removerDaPartida(partida.id());
			enviador.enviar(Mensagens.JOGO_CANCELADO);
		} else {
			enviador.enviar(Mensagens.RESULTADO_TRAVA_CANCELAMENTO);
		}
	}
}
