package br.com.aabb.sorteio;

import br.com.aabb.partida.TipoConfirmado;

/**
 * Jogador escalado em um time.
 *
 * @param jogadorId {@code null} para convidados
 * @param goleiro   goleiro designado do time (no máximo um por time)
 */
public record Escalado(TipoConfirmado tipo, String jogadorId, String nome, boolean goleiro) {

	public boolean membro() {
		return tipo == TipoConfirmado.MEMBRO;
	}
}
