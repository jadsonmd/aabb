package br.com.aabb.fluxo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import br.com.aabb.comando.Mensagens;
import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.Posicao;
import br.com.aabb.lance.Lance;
import br.com.aabb.lance.StatusLance;
import br.com.aabb.partida.PartidaService;
import br.com.aabb.sorteio.Sorteio;

class VarEResultadoTest extends TesteDeIntegracao {

	@Autowired
	PartidaService partidas;

	Jogador adm;
	Jogador joao;
	Jogador leandro;
	List<Jogador> votantes;

	@BeforeEach
	void cadastrar() {
		adm = admin("Jadson", "5511900000001", 7, Posicao.M);
		joao = membro("João", "5511900000002", 7, Posicao.A);
		leandro = membro("Leandro", "5511900000003", 7, Posicao.Z);
		votantes = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			votantes.add(membro("V" + i, "55119200000" + i, 5, Posicao.M));
		}
	}

	@Test
	void canetaSoValeComTresJoinhasDePessoasDiferentes() {
		String lance = texto(adm, "@5511900000002 #canetou @5511900000003", mencao(joao), mencao(leandro));
		assertThat(ultimaMensagem()).isEqualTo("🔍 Caneta de João na cabine do VAR. Precisa de 3 👍 para valer.");

		reagir(joao, lance, "👍"); // autor não vota
		reagir(votantes.get(0), lance, "👍");
		reagir(votantes.get(0), lance, "👍"); // mesmo votante não conta duas vezes
		reagir(votantes.get(1), lance, "👍");
		reagir(votantes.get(1), lance, ""); // removeu a reação
		reagir(votantes.get(2), lance, "👍🏽");
		assertThat(recarregar(joao).skill()).isEqualTo(7.0);

		reagir(votantes.get(3), lance, "👍");
		assertThat(recarregar(joao).skill()).isEqualTo(7.2);
		assertThat(recarregar(leandro).skill()).isEqualTo(6.8);
		assertThat(ultimaMensagem()).startsWith("✅ VAR confirmou: Caneta de João em Leandro!");

		// Depois de aprovado, novas reações não mudam nada.
		reagir(votantes.get(1), lance, "👍");
		assertThat(recarregar(joao).skill()).isEqualTo(7.2);
	}

	@Test
	void semAutorAntesDaHashtagOAutorEhQuemEnviou() {
		// O WhatsApp não deixa mencionar a si mesmo: "#canetou @leandro" é uma caneta de quem enviou.
		String lance = texto(joao, "#canetou @5511900000003", mencao(leandro));
		assertThat(ultimaMensagem()).startsWith("🔍 Caneta de João na cabine do VAR");
		reagir(joao, lance, "👍"); // continua sem poder votar no próprio lance
		votantes.subList(0, 2).forEach(v -> reagir(v, lance, "👍"));
		assertThat(recarregar(joao).skill()).isEqualTo(7.0);
		reagir(votantes.get(2), lance, "👍");
		assertThat(recarregar(joao).skill()).isEqualTo(7.2);
		assertThat(recarregar(leandro).skill()).isEqualTo(6.8);

		texto(leandro, "eu #chapelou @5511900000002", mencao(joao));
		assertThat(ultimaMensagem()).startsWith("🔍 Chapéu de Leandro");

		texto(joao, "#canetou");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.USO_JOGADA);
	}

	@Test
	void jogadaComQuantidadeMultiplicaOPeso() {
		String canetas = texto(joao, "#canetou @5511900000003 3x", mencao(leandro));
		assertThat(ultimaMensagem()).startsWith("🔍 3 canetas de João na cabine do VAR");
		votantes.subList(0, 3).forEach(v -> reagir(v, canetas, "👍"));
		assertThat(recarregar(joao).skill()).isEqualTo(7.6);
		assertThat(recarregar(leandro).skill()).isEqualTo(6.4);
		assertThat(ultimaMensagem()).startsWith("✅ VAR confirmou: 3 canetas de João em Leandro!");

		String chapeus = texto(adm, "@5511900000003 #chapelou @5511900000002 2x", mencao(leandro), mencao(joao));
		assertThat(ultimaMensagem()).startsWith("🔍 2 chapéus de Leandro");
		votantes.subList(0, 3).forEach(v -> reagir(v, chapeus, "👍"));
		assertThat(recarregar(leandro).skill()).isEqualTo(6.8);
		assertThat(recarregar(joao).skill()).isEqualTo(7.2);

		texto(joao, "#canetou @5511900000003 11x", mencao(leandro));
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.QUANTIDADE_INVALIDA);
	}

	@Test
	void chapeuEmConvidadoSoAlteraOLadoIdentificado() {
		String lance = texto(adm, "marcelo #chapelou @5511900000003", mencao(leandro));
		assertThat(ultimaMensagem()).contains("Chapéu de marcelo").contains("(marcelo não identificado");
		votantes.subList(0, 3).forEach(v -> reagir(v, lance, "👍"));
		assertThat(recarregar(leandro).skill()).isEqualTo(6.8);

		texto(adm, "marcelo #canetou felipe");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.NINGUEM_IDENTIFICADO);
	}

	@Test
	void aprendeLidEReconheceMencaoPorLid() {
		// João manda mensagem identificado pelo LID, com o número como participantAlt: vínculo aprendido.
		texto("777000111@lid", mencao(joao), "oi");
		assertThat(recarregar(joao).lids()).containsExactly("777000111");

		String lance = texto(adm, "@777000111 #canetou @5511900000003", "777000111@lid", mencao(leandro));
		assertThat(ultimaMensagem()).startsWith("🔍 Caneta de João");
		votantes.subList(0, 3).forEach(v -> reagir(v, lance, "👍"));
		assertThat(recarregar(joao).skill()).isEqualTo(7.2);

		// Votar por LID e por número conta como a mesma pessoa.
		String outro = texto(adm, "@5511900000003 #chapelou @5511900000001", mencao(leandro), mencao(adm));
		reagir(votantes.get(0), outro, "👍");
		texto("777000111@lid", null, "só reagindo");
		reagir(joao, outro, "👍");
		assertThat(mongo.findOne(Query.query(Criteria.where("messageId").is(outro)), Lance.class).approvers())
				.hasSize(2);
	}

	@Test
	void reconheceCelularCadastradoComNonoDigitoQuandoOWhatsAppEnviaSemEle() {
		// Cadastro com 13 dígitos; o WhatsApp manda o participantAlt com 12 (sem o nono dígito) e o LID.
		Jogador jadson = membro("Jadinho", "5548999990011", 7, Posicao.M);
		texto("100000000000001@lid", "554899990011@s.whatsapp.net", "@111222333 #canetou @5511900000003",
				"111222333@lid", mencao(leandro));

		assertThat(recarregar(jadson).lids()).containsExactly("100000000000001");
		assertThat(ultimaMensagem()).startsWith("🔍 Caneta de @111222333").contains("não identificado");
	}

	@Test
	void naoPermiteCadastrarOMesmoCelularComESemONonoDigito() {
		membro("Jadinho", "5548999990011", 7, Posicao.M);
		assertThatThrownBy(() -> membro("Outro", "554899990011", 5, Posicao.M))
				.hasMessageContaining("Já existe jogador com esse WhatsApp");
	}

	@Test
	void golsResultadoEPresencaNoFluxoCompleto() {
		texto(adm, "#abrirlista");
		List<Jogador> todos = new ArrayList<>(List.of(adm, joao, leandro));
		todos.addAll(votantes);
		for (int i = 0; i < 4; i++) {
			todos.add(membro("X" + i, "55119300000" + i, 6, Posicao.M));
		}
		texto(joao, "@5511900000002 #marcou 2", mencao(joao));
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.SEM_SORTEIO);

		todos.forEach(j -> texto(j, "#vou"));
		texto(adm, "#convidado Marcelo");
		texto(adm, "#sortear");
		Sorteio sorteio = partidas.doDia(SEGUNDA_10H.toLocalDate()).orElseThrow().sorteioVigente().orElseThrow();

		// Sem menção, os gols são de quem enviou.
		texto(leandro, "#marcou");
		assertThat(ultimaMensagem()).isEqualTo("🔍 1 gol de Leandro na cabine do VAR. Precisa de 3 👍 para valer.");

		// Gols de convidado não contam.
		texto(adm, "Marcelo #marcou 3x");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.golsNaoComputados("Marcelo"));
		texto(adm, "@5511900000002 #marcou 15x", mencao(joao));
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.QUANTIDADE_INVALIDA);
		texto(adm, "Marcelo #marcou 3");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.golsNaoComputados("Marcelo"));

		// Dois #marcou pendentes para o João: vale o primeiro aprovado, o outro é descartado.
		String gols = texto(adm, "@5511900000002 #marcou 2x", mencao(joao));
		String repetido = texto(leandro, "@5511900000002 #marcou 5", mencao(joao));
		assertThat(ultimaMensagem()).isEqualTo("🔍 5 gols de João na cabine do VAR. Precisa de 3 👍 para valer.");
		reagir(joao, gols, "👍"); // quem marcou não vota
		votantes.subList(0, 3).forEach(v -> reagir(v, gols, "👍"));
		assertThat(recarregar(joao).skill()).isEqualTo(7.6);
		assertThat(ultimaMensagem()).isEqualTo("⚽ VAR confirmou: 2 gols de João! +0.6 de skill.");
		votantes.subList(0, 3).forEach(v -> reagir(v, repetido, "👍"));
		assertThat(recarregar(joao).skill()).isEqualTo(7.6);
		texto(adm, "@5511900000002 #marcou 1", mencao(joao));
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.golsJaRegistrados("João", 2));
		assertThat(mongo.findAll(Lance.class)).filteredOn(l -> l.status() == StatusLance.APPROVED).hasSize(1);

		// Resultado: qualquer pessoa do grupo, mesmo sem cadastro; vale o primeiro.
		var antes = sorteio.azul().membros().stream().map(id -> jogadores.buscar(id).skill()).toList();
		texto("5511988887777@s.whatsapp.net", null, "#timeazulganhou");
		assertThat(ultimaMensagem()).startsWith("🏆 Time Azul venceu! +0.3 de skill para:");
		var depois = sorteio.azul().membros().stream().map(id -> jogadores.buscar(id).skill()).toList();
		for (int i = 0; i < antes.size(); i++) {
			assertThat(depois.get(i)).isEqualTo(Math.min(10, Math.round((antes.get(i) + 0.3) * 100) / 100.0));
		}
		texto(adm, "#empate");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.resultadoJaRegistrado("Time Azul venceu"));
		texto(adm, "#ressortear");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.RESULTADO_TRAVA_TIMES);
		texto(adm, "#jogocancelado");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.RESULTADO_TRAVA_CANCELAMENTO);
	}
}
