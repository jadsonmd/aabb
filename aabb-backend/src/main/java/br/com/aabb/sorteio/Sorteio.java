package br.com.aabb.sorteio;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Um sorteio de times. Com a mesma lista, a mesma {@code seed} e a mesma {@code versaoAlgoritmo},
 * o resultado é sempre idêntico.
 *
 * @param composicao chave da divisão dos titulares, usada para o {@code #ressortear} não repetir times
 */
public record Sorteio(
		int versao,
		long seed,
		int versaoAlgoritmo,
		Time azul,
		Time amarelo,
		String composicao,
		Instant criadoEm) {

	/** Ids de todos os membros sorteados (titulares e reservas dos dois times). */
	public List<String> membros() {
		return Stream.concat(azul.membros().stream(), amarelo.membros().stream()).toList();
	}
}
