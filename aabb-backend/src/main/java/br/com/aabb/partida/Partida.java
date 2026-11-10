package br.com.aabb.partida;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import br.com.aabb.sorteio.Sorteio;

/**
 * Partida de um dia de jogo. A lista ({@code confirmados}) começa e termina no próprio dia.
 *
 * @param data         dia do jogo (ISO, ex: 2026-11-09); único
 * @param horario      horário do jogo ("HH:mm"); {@code null} = horário padrão da agenda
 * @param sorteioAtual versão do sorteio vigente (0 = nenhum)
 * @param eventos      assistências e gols contra lançados no painel (só histórico, sem skill)
 */
@Document("partidas")
public record Partida(
		@Id String id,
		String data,
		String horario,
		StatusPartida status,
		List<Confirmado> confirmados,
		List<Sorteio> sorteios,
		int sorteioAtual,
		Resultado resultado,
		String resultadoInformadoPor,
		Instant resultadoEm,
		Integer placarAzul,
		Integer placarAmarelo,
		List<EventoDaPartida> eventos,
		Instant finalizadaEm,
		Instant criadaEm) {

	public Partida {
		confirmados = confirmados == null ? List.of() : List.copyOf(confirmados);
		sorteios = sorteios == null ? List.of() : List.copyOf(sorteios);
		eventos = eventos == null ? List.of() : List.copyOf(eventos);
	}

	public LocalDate dia() {
		return LocalDate.parse(data);
	}

	public Optional<LocalTime> horarioDefinido() {
		return Optional.ofNullable(horario).map(LocalTime::parse);
	}

	public Optional<Sorteio> sorteioVigente() {
		return sorteios.stream().filter(s -> s.versao() == sorteioAtual).findFirst();
	}

	public boolean temResultado() {
		return resultado != null;
	}
}
