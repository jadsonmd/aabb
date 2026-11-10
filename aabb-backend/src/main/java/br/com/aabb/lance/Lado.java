package br.com.aabb.lance;

/**
 * Autor ou vítima de um lance.
 *
 * @param jogadorId {@code null} quando não identificado (convidado ou {@code @lid} desconhecido)
 * @param jid       JID original da menção, quando houver
 * @param nome      nome exibido
 */
public record Lado(String jogadorId, String jid, String nome) {

	public boolean identificado() {
		return jogadorId != null;
	}
}
