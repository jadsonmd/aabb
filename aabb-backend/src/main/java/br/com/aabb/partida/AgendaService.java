package br.com.aabb.partida;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Service;

import br.com.aabb.config.AabbProperties;

/**
 * Responde "hoje é dia de jogo?" no fuso configurado (America/Sao_Paulo).
 */
@Service
public class AgendaService {

	private final AabbProperties.Agenda agenda;
	private final Clock clock;

	AgendaService(AabbProperties properties, Clock clock) {
		this.agenda = properties.agenda();
		this.clock = clock;
	}

	public LocalDate hoje() {
		return LocalDate.now(clock.withZone(agenda.zona()));
	}

	public boolean hojeTemJogo() {
		return agenda.dias().contains(hoje().getDayOfWeek());
	}

	/** Ex: "sábado, 9h" (horário da partida ou o padrão da agenda). */
	public String descricaoDoJogo(Partida partida) {
		return descricaoDoJogo(partida.dia(), partida.horarioDefinido().orElse(agenda.horario()));
	}

	/** Ex: "segunda, 19h". */
	public String descricaoDoJogo(LocalDate dia) {
		return descricaoDoJogo(dia, agenda.horario());
	}

	public String descricaoDoJogo(LocalDate dia, LocalTime horario) {
		String nomeDia = switch (dia.getDayOfWeek()) {
			case MONDAY -> "segunda";
			case TUESDAY -> "terça";
			case WEDNESDAY -> "quarta";
			case THURSDAY -> "quinta";
			case FRIDAY -> "sexta";
			case SATURDAY -> "sábado";
			case SUNDAY -> "domingo";
		};
		String hora = horario.getMinute() == 0 ? horario.getHour() + "h"
				: "%dh%02d".formatted(horario.getHour(), horario.getMinute());
		return nomeDia + ", " + hora;
	}

	boolean diaDeJogo(DayOfWeek dia) {
		return agenda.dias().contains(dia);
	}
}
