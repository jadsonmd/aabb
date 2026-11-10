package br.com.aabb.fluxo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.aabb.comando.Mensagens;
import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.Posicao;

class CadastroAutomaticoTest extends TesteDeIntegracao {

	@Test
	void quemNaoTemCadastroViraMembroNaHoraComSkill5ELateral() {
		Jogador adm = admin("Jadson", "5511900000001", 7, Posicao.M);
		texto(adm, "#abrirlista");

		// O primeiro #vou já cadastra e confirma.
		textoComNome("777000111@lid", "5548999990000@s.whatsapp.net", "Marcos Souza", "#vou");
		assertThat(ultimaMensagem()).startsWith("✅ Marcos Souza confirmado!");

		textoComNome("777000111@lid", "5548999990000@s.whatsapp.net", "Marcos Souza", "bora");
		Jogador marcos = jogadores.listar().stream().filter(j -> j.apelido().equals("Marcos Souza")).findFirst()
				.orElseThrow();
		assertThat(jogadores.listar()).hasSize(2);
		assertThat(marcos.whatsapp()).isEqualTo("5548999990000");
		assertThat(marcos.lids()).containsExactly("777000111");
		assertThat(marcos.skill()).isEqualTo(5.0);
		assertThat(marcos.posicoes()).containsExactly(Posicao.L);
		assertThat(marcos.ativo()).isTrue();
		assertThat(marcos.admin()).isFalse();
	}

	@Test
	void desativadoNaoEhRecadastradoESoLidNaoGeraCadastro() {
		textoComNome("888@lid", "5548988887777@s.whatsapp.net", "Visitante", "oi");
		Jogador visitante = jogadores.listar().getFirst();
		jogadores.definirAtivo(visitante.id(), false);

		textoComNome("888@lid", "5548988887777@s.whatsapp.net", "Visitante", "#canetou @123");
		assertThat(jogadores.listar()).hasSize(1);
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.NAO_RECONHECIDO);

		// Sem número de telefone não há como cadastrar.
		textoComNome("999@lid", null, "Só LID", "oi");
		assertThat(jogadores.listar()).hasSize(1);
	}

	@Test
	void membroJaCadastradoComONonoDigitoNaoGeraOutroCadastro() {
		membro("Jadinho", "5548999990011", 7, Posicao.M);
		textoComNome("100000000000001@lid", "554899990011@s.whatsapp.net", "Jadson", "oi");
		assertThat(jogadores.listar()).hasSize(1);
	}
}
