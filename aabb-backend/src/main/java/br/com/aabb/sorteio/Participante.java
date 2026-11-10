package br.com.aabb.sorteio;

import br.com.aabb.jogador.Posicao;
import br.com.aabb.partida.TipoConfirmado;

/**
 * Entrada do algoritmo de sorteio.
 *
 * @param chave     identificador único na lista (id do membro ou nome normalizado do convidado)
 * @param skill     skill atual (0 para convidados)
 * @param principal posição principal ({@code null} para convidados)
 */
public record Participante(TipoConfirmado tipo, String chave, String jogadorId, String nome, double skill,
		Posicao principal) {

	public boolean membro() {
		return tipo == TipoConfirmado.MEMBRO;
	}

	public boolean goleiro() {
		return principal == Posicao.G;
	}
}
