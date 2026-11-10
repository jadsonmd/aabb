package br.com.aabb.whatsapp;

/**
 * Identificador do WhatsApp já normalizado (sem sufixo de dispositivo).
 *
 * @param usuario parte antes do {@code @} (número ou LID)
 * @param tipo    tipo do identificador, deduzido pelo sufixo
 */
public record Jid(String usuario, Tipo tipo) {

	public enum Tipo { NUMERO, LID, GRUPO, OUTRO }

	/** Normaliza um JID bruto. Retorna {@code null} se vier vazio. */
	public static Jid of(String bruto) {
		if (bruto == null || bruto.isBlank()) {
			return null;
		}
		String valor = bruto.trim();
		int arroba = valor.indexOf('@');
		String usuario = arroba >= 0 ? valor.substring(0, arroba) : valor;
		String servidor = arroba >= 0 ? valor.substring(arroba + 1) : "";
		int doisPontos = usuario.indexOf(':');
		if (doisPontos >= 0) {
			usuario = usuario.substring(0, doisPontos);
		}
		Tipo tipo = switch (servidor) {
			case "s.whatsapp.net", "c.us" -> Tipo.NUMERO;
			case "lid" -> Tipo.LID;
			case "g.us" -> Tipo.GRUPO;
			default -> Tipo.OUTRO;
		};
		return new Jid(usuario, tipo);
	}

	public static Jid numero(String numero) {
		return new Jid(numero, Tipo.NUMERO);
	}

	public boolean isNumero() {
		return tipo == Tipo.NUMERO;
	}

	public boolean isLid() {
		return tipo == Tipo.LID;
	}

	/** JID completo, no formato aceito pela Evolution API. */
	public String completo() {
		return switch (tipo) {
			case NUMERO -> usuario + "@s.whatsapp.net";
			case LID -> usuario + "@lid";
			case GRUPO -> usuario + "@g.us";
			case OUTRO -> usuario;
		};
	}

	@Override
	public String toString() {
		return completo();
	}
}
