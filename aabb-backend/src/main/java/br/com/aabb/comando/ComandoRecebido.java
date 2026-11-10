package br.com.aabb.comando;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.aabb.partida.Nomes;

/**
 * Primeira hashtag conhecida de um texto, com o que vem antes e depois dela.
 * Ex: "@5511 #marcou 4" → comando MARCOU, antes "@5511", depois "4".
 */
public record ComandoRecebido(Comando comando, String antes, String depois) {

	private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}\\p{N}_]+)");

	public static Optional<ComandoRecebido> extrair(String texto) {
		if (texto == null) {
			return Optional.empty();
		}
		Matcher matcher = HASHTAG.matcher(texto);
		while (matcher.find()) {
			String hashtag = Nomes.normalizar(matcher.group(1)).toLowerCase(Locale.ROOT);
			var comando = Comando.daHashtag(hashtag);
			if (comando.isPresent()) {
				return Optional.of(new ComandoRecebido(comando.get(),
						texto.substring(0, matcher.start()).trim(),
						texto.substring(matcher.end()).trim()));
			}
		}
		return Optional.empty();
	}
}
