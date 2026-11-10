package br.com.aabb.lance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuantidadeTest {

	@Test
	void leAQuantidadeComOuSemX() {
		assertThat(Quantidade.extrair("@5511900000003 4x", 10)).isEqualTo(new Quantidade(4, true, "@5511900000003"));
		assertThat(Quantidade.extrair("2X", 10)).isEqualTo(new Quantidade(2, true, ""));
		assertThat(Quantidade.extrair("3 x", 10).valor()).isEqualTo(3);
		assertThat(Quantidade.extrair("2", 10).valor()).isEqualTo(2);
	}

	@Test
	void semQuantidadeValeUmENaoConfundeNumeroDaMencao() {
		assertThat(Quantidade.extrair("@5511900000003", 10)).isEqualTo(new Quantidade(1, true, "@5511900000003"));
		assertThat(Quantidade.extrair("", 10)).isEqualTo(new Quantidade(1, true, ""));
		assertThat(Quantidade.extrair(null, 10).valor()).isEqualTo(1);
	}

	@Test
	void foraDoIntervaloEhInvalida() {
		assertThat(Quantidade.extrair("11x", 10).valida()).isFalse();
		assertThat(Quantidade.extrair("0x", 10).valida()).isFalse();
		assertThat(Quantidade.extrair("99999x", 10).valida()).isFalse();
	}
}
