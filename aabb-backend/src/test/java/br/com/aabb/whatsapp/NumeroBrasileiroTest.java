package br.com.aabb.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NumeroBrasileiroTest {

	@Test
	void celularComESemONonoDigitoSaoOMesmoNumero() {
		assertThat(NumeroBrasileiro.variantes("5548999990011")).containsExactly("5548999990011", "554899990011");
		assertThat(NumeroBrasileiro.variantes("554899990011")).containsExactly("554899990011", "5548999990011");
	}

	@Test
	void fixoENumeroEstrangeiroNaoGanhamVariante() {
		assertThat(NumeroBrasileiro.variantes("554833334444")).containsExactly("554833334444");
		assertThat(NumeroBrasileiro.variantes("14155550100")).containsExactly("14155550100");
	}
}
