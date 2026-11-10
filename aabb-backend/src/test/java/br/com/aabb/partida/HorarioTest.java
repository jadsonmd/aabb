package br.com.aabb.partida;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class HorarioTest {

	@Test
	void aceitaOsFormatosComuns() {
		assertThat(Horario.ler("9h")).contains(LocalTime.of(9, 0));
		assertThat(Horario.ler("9")).contains(LocalTime.of(9, 0));
		assertThat(Horario.ler("20h30")).contains(LocalTime.of(20, 30));
		assertThat(Horario.ler("20:30")).contains(LocalTime.of(20, 30));
		assertThat(Horario.ler(" 20H30min ")).contains(LocalTime.of(20, 30));
	}

	@Test
	void recusaHorarioInvalido() {
		assertThat(Horario.ler("25h")).isEmpty();
		assertThat(Horario.ler("20h75")).isEmpty();
		assertThat(Horario.ler("amanhã")).isEmpty();
	}
}
