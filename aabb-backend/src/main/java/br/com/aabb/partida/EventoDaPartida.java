package br.com.aabb.partida;

/**
 * Evento lançado no painel após o jogo. Fica só no histórico: não altera skill.
 *
 * @param jogadorId {@code null} para convidados
 */
public record EventoDaPartida(Tipo tipo, String jogadorId, String nome) {

	public enum Tipo { ASSISTENCIA, GOL_CONTRA }
}
