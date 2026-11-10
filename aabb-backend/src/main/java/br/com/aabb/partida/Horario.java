package br.com.aabb.partida;

import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lê o horário informado no {@code #abrirlista}: "9h", "9", "20h30", "20:30", "20h30min".
 */
final class Horario {

	private static final Pattern FORMATO = Pattern.compile("^(\\d{1,2})(?:\\s*[h:]\\s*(\\d{2})?)?\\s*(?:min)?$");

	private Horario() {
	}

	static Optional<LocalTime> ler(String texto) {
		var matcher = FORMATO.matcher(texto.trim().toLowerCase(Locale.ROOT));
		if (!matcher.matches()) {
			return Optional.empty();
		}
		int hora = Integer.parseInt(matcher.group(1));
		int minuto = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
		if (hora > 23 || minuto > 59) {
			return Optional.empty();
		}
		return Optional.of(LocalTime.of(hora, minuto));
	}
}
