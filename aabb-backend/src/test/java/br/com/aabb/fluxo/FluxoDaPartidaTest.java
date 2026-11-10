package br.com.aabb.fluxo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.aabb.comando.Mensagens;
import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.Posicao;
import br.com.aabb.partida.Partida;
import br.com.aabb.partida.PartidaService;
import br.com.aabb.partida.StatusPartida;
import br.com.aabb.partida.TipoConfirmado;
import br.com.aabb.presenca.Presenca;
import tools.jackson.databind.json.JsonMapper;

class FluxoDaPartidaTest extends TesteDeIntegracao {

	private static final LocalDate SEGUNDA = SEGUNDA_10H.toLocalDate();

	@Autowired
	PartidaService partidas;

	@Test
	void comandosDeListaSoFuncionamComAListaAbertaENoDiaDoJogo() {
		Jogador adm = admin("Jadson", "5511900000001", 7, Posicao.M);
		Jogador joao = membro("João", "5511900000002", 6, Posicao.A);

		texto(joao, "#vou");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.LISTA_NAO_ABERTA);

		texto(joao, "#abrirlista");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.SO_ADMIN);

		texto(adm, "#abrirlista");
		assertThat(ultimaMensagem()).startsWith("⚽ Lista aberta para o jogo de hoje (segunda, 19h)");
		texto(adm, "#abrirlista");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.LISTA_JA_ABERTA);

		// Terça não é dia de jogo: nada acontece.
		relogio.definir(SEGUNDA_10H.plusDays(1));
		int antes = enviador.mensagens.size();
		texto(joao, "#vou");
		assertThat(enviador.mensagens).hasSize(antes);
	}

	@Test
	void adminAbreJogoExtraEmQualquerDiaComHorarioOpcional() {
		Jogador adm = admin("Jadson", "5511900000001", 7, Posicao.M);
		Jogador joao = membro("João", "5511900000002", 6, Posicao.A);
		relogio.definir(SEGUNDA_10H.plusDays(5)); // sábado

		int antes = enviador.mensagens.size();
		texto(joao, "#vou");
		assertThat(enviador.mensagens).hasSize(antes); // sem jogo hoje: ignorado em silêncio

		texto(joao, "#abrirlista 9h");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.SO_ADMIN);
		texto(adm, "#abrirlista amanhã");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.HORARIO_INVALIDO);

		texto(adm, "#abrirlista 9h");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.listaAberta("sábado, 9h"));

		// Com o jogo extra aberto, os comandos de lista funcionam no sábado.
		texto(joao, "#vou");
		assertThat(ultimaMensagem()).contains("⚽ Jogo de hoje — sábado, 9h");

		texto(adm, "#abrirlista 10h30");
		assertThat(ultimaMensagem()).isEqualTo("🕗 Horário do jogo de hoje alterado: sábado, 10h30.");
		texto(joao, "#lista");
		assertThat(ultimaMensagem()).contains("sábado, 10h30");

		texto(adm, "#abrirlista");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.LISTA_JA_ABERTA);
	}

	@Test
	void listaEmOrdemDeConfirmacaoComConvidados() {
		Jogador adm = admin("Jadson", "5511900000001", 7, Posicao.M);
		Jogador joao = membro("João", "5511900000002", 6, Posicao.A);
		texto(adm, "#abrirlista");

		texto(joao, "#vou");
		texto(adm, "bora #vou");
		texto(adm, "#convidado  Marcélo ");
		assertThat(ultimaMensagem()).isEqualTo("""
				✅ Marcélo (convidado de Jadson) entrou na lista!

				⚽ Jogo de hoje — segunda, 19h
				Confirmados (3):
				1. João
				2. Jadson
				3. Marcélo (convidado de Jadson)""");

		texto(joao, "#vou");
		assertThat(ultimaMensagem()).isEqualTo("João já está na lista.");
		texto(joao, "#convidado marcelo");
		assertThat(ultimaMensagem()).isEqualTo("marcelo já está na lista.");

		texto(joao, "#desconvidar MARCELO");
		assertThat(ultimaMensagem()).isEqualTo("Marcélo removido da lista. Confirmados: 2.");
		texto(joao, "#dm");
		assertThat(ultimaMensagem()).isEqualTo("João saiu da lista. Confirmados: 1.");
		texto(joao, "#dm");
		assertThat(ultimaMensagem()).isEqualTo("João não está na lista.");

		texto(joao, "#lista");
		assertThat(ultimaMensagem()).endsWith("Confirmados (1):\n1. Jadson");
	}

	@Test
	void remetenteSoComLidNaoEntraNaLista() {
		Jogador adm = admin("Jadson", "5511900000001", 7, Posicao.M);
		texto(adm, "#abrirlista");
		texto("123456@lid", null, "#vou");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.NAO_RECONHECIDO);
		assertThat(partidas.doDia(SEGUNDA).orElseThrow().confirmados()).isEmpty();
	}

	@Test
	void vouSimultaneosNaoDuplicamNemPerdemConfirmacoes() throws Exception {
		Jogador adm = admin("Jadson", "5511900000001", 7, Posicao.M);
		texto(adm, "#abrirlista");
		List<Jogador> membros = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			membros.add(membro("J" + i, "55119100000" + String.format("%02d", i), 5, Posicao.M));
		}
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (Jogador m : membros) {
				// Cada membro manda #vou duas vezes ao mesmo tempo.
				executor.submit(() -> texto(m, "#vou"));
				executor.submit(() -> texto(m, "#vou"));
			}
		}
		Partida partida = partidas.doDia(SEGUNDA).orElseThrow();
		assertThat(partida.confirmados()).hasSize(20);
		assertThat(partida.confirmados().stream().map(c -> c.jogadorId()).distinct()).hasSize(20);
	}

	@Test
	void sorteioComPrioridadeDeMembrosPresencaERessorteio() {
		Jogador adm = admin("Jadson", "5511900000001", 7, Posicao.G);
		texto(adm, "#abrirlista");
		texto(adm, "#vou");
		texto(adm, "#convidado Marcelo");
		texto(adm, "#convidado Felipe");
		List<Jogador> membros = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			Jogador m = membro("J" + i, "55119100000" + String.format("%02d", i), 4 + i, i == 0 ? Posicao.G : Posicao.M);
			membros.add(m);
			texto(m, "#vou");
		}

		texto(adm, "#sortear");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.minimoParaSortear(9, 10));

		for (int i = 6; i < 13; i++) {
			Jogador m = membro("J" + i, "55119100000" + String.format("%02d", i), 3 + i * 0.3, Posicao.Z);
			membros.add(m);
			texto(m, "#vou");
		}
		// 14 membros + 2 convidados: convidados ficam de reserva, mesmo tendo confirmado antes.
		texto(adm, "#sortear");
		assertThat(ultimaMensagem()).startsWith("⚽ Times de hoje — segunda, 19h");

		Partida partida = partidas.doDia(SEGUNDA).orElseThrow();
		assertThat(partida.status()).isEqualTo(StatusPartida.SORTEADA);
		var sorteio = partida.sorteioVigente().orElseThrow();
		assertThat(sorteio.azul().titulares()).hasSize(7).allMatch(e -> e.tipo() == TipoConfirmado.MEMBRO);
		assertThat(sorteio.amarelo().titulares()).hasSize(7).allMatch(e -> e.tipo() == TipoConfirmado.MEMBRO);
		assertThat(sorteio.azul().goleiro()).isPresent();
		assertThat(sorteio.amarelo().goleiro()).isPresent();
		assertThat(sorteio.azul().reservas()).hasSize(1);
		assertThat(sorteio.amarelo().reservas()).hasSize(1);
		assertThat(mongo.findAll(Presenca.class)).hasSize(14);

		texto(membros.getFirst(), "#vou");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.JA_SORTEADO);
		texto(adm, "#sortear");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.JA_SORTEADO_USE_RESSORTEAR);

		texto(adm, "#ressortear");
		assertThat(ultimaMensagem()).startsWith("🔄 Novo sorteio (#2)");
		var depois = partidas.doDia(SEGUNDA).orElseThrow();
		assertThat(depois.sorteios()).hasSize(2);
		assertThat(depois.sorteios().get(1).composicao()).isNotEqualTo(depois.sorteios().get(0).composicao());
		assertThat(mongo.findAll(Presenca.class)).hasSize(14);

		texto(membros.getFirst(), "#lista");
		assertThat(ultimaMensagem()).startsWith("🔄 Novo sorteio (#2)");
	}

	@Test
	void cancelamentoDepoisDoSorteioRemovePresencasEEDefinitivo() {
		Jogador adm = admin("Jadson", "5511900000001", 7, Posicao.M);
		texto(adm, "#abrirlista");
		texto(adm, "#vou");
		for (int i = 0; i < 9; i++) {
			texto(membro("J" + i, "55119100000" + String.format("%02d", i), 5, Posicao.M), "#vou");
		}
		texto(adm, "#sortear");
		assertThat(mongo.findAll(Presenca.class)).hasSize(10);

		texto(adm, "#jogocancelado");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.JOGO_CANCELADO);
		assertThat(mongo.findAll(Presenca.class)).isEmpty();

		texto(adm, "#abrirlista");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.CANCELADO);
		texto(adm, "#vou");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.CANCELADO);
	}

	@Test
	void mensagemReenviadaPelaEvolutionEProcessadaUmaVezSo() {
		Jogador adm = admin("Jadson", "5511900000001", 7, Posicao.M);
		texto(adm, "#abrirlista");
		var json = JsonMapper.builder().build();
		var payload = json.readTree("""
				{"event":"messages.upsert","data":{"key":{"remoteJid":"%s","participant":"%s","id":"REPETIDA"},
				 "message":{"conversation":"#vou"}}}
				""".formatted(GRUPO, mencao(adm)));
		webhook.receber(payload);
		int depoisDaPrimeira = enviador.mensagens.size();
		webhook.receber(payload);
		assertThat(enviador.mensagens).hasSize(depoisDaPrimeira);
	}
}
