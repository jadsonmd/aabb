package br.com.aabb.whatsapp;

import java.util.List;

/**
 * Envio de mensagens para o grupo oficial.
 */
public interface EnviadorWhatsApp {

	/**
	 * Envia um texto ao grupo. Falhas são registradas em log e não interrompem o fluxo de negócio.
	 *
	 * @param mencoes JIDs a mencionar; o texto deve conter o {@code @numero} correspondente
	 */
	void enviar(String texto, List<Jid> mencoes);

	default void enviar(String texto) {
		enviar(texto, List.of());
	}
}
