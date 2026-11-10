package br.com.aabb.whatsapp;

import java.util.List;

/**
 * Mensagem do grupo já extraída do webhook {@code MESSAGES_UPSERT} da Evolution API.
 *
 * @param messageId    id único da mensagem (chave de idempotência)
 * @param grupo        JID do chat (grupo) de origem
 * @param remetente    JIDs conhecidos de quem enviou ({@code participant} e {@code participantAlt})
 * @param nomeExibido  nome do remetente no WhatsApp ({@code pushName})
 * @param fromMe       mensagem enviada pelo próprio bot
 * @param texto        texto da mensagem (vazio se não houver)
 * @param mencoes      JIDs mencionados ({@code contextInfo.mentionedJid})
 * @param reacao       reação, quando a mensagem for uma reação
 */
public record MensagemRecebida(
		String messageId,
		Jid grupo,
		List<Jid> remetente,
		String nomeExibido,
		boolean fromMe,
		String texto,
		List<Jid> mencoes,
		Reacao reacao) {

	/**
	 * @param alvoMessageId id da mensagem que recebeu a reação
	 * @param emoji         emoji da reação; vazio quando a reação foi removida
	 */
	public record Reacao(String alvoMessageId, String emoji) {

		public boolean positiva() {
			return emoji != null && emoji.startsWith("👍");
		}
	}

	public boolean isReacao() {
		return reacao != null;
	}
}
