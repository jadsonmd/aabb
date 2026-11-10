package br.com.aabb.comando;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComandoRecebidoTest {

	@Test
	void separaOQueVemAntesEDepoisDaHashtag() {
		var recebido = ComandoRecebido.extrair("@5511900000000 #marcou 4").orElseThrow();
		assertThat(recebido.comando()).isEqualTo(Comando.MARCOU);
		assertThat(recebido.antes()).isEqualTo("@5511900000000");
		assertThat(recebido.depois()).isEqualTo("4");
	}

	@Test
	void ignoraMaiusculasEAcentosEHashtagsDesconhecidas() {
		assertThat(ComandoRecebido.extrair("bora #partiu #VOU").orElseThrow().comando()).isEqualTo(Comando.VOU);
		assertThat(ComandoRecebido.extrair("#Convidado Marcelo").orElseThrow().depois()).isEqualTo("Marcelo");
	}

	@Test
	void naoConfundeHashtagMaiorComComando() {
		assertThat(ComandoRecebido.extrair("#voucher")).isEmpty();
		assertThat(ComandoRecebido.extrair("sem comando")).isEmpty();
	}
}
