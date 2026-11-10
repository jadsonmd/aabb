package br.com.aabb.lance;

import java.util.regex.Pattern;

/**
 * Quantidade de um lance escrita como "4x" (ou só "4"), ex: "#canetou @joao 2x", "#marcou 3x".
 * Números de menções ("@5511...") não são confundidos com quantidade.
 *
 * @param valor    quantidade informada, ou 1 se não houver
 * @param valida   {@code false} se o número estiver fora do intervalo permitido
 * @param restante texto sem a quantidade
 */
record Quantidade(int valor, boolean valida, String restante) {

	private static final Pattern NUMERO = Pattern.compile("(?<![@\\p{L}\\d])(\\d+)\\s*[xX]?(?![\\p{L}\\d])");

	static Quantidade extrair(String texto, int maximo) {
		if (texto == null) {
			return new Quantidade(1, true, "");
		}
		var matcher = NUMERO.matcher(texto);
		if (!matcher.find()) {
			return new Quantidade(1, true, texto.trim());
		}
		String restante = (texto.substring(0, matcher.start()) + " " + texto.substring(matcher.end())).trim();
		String digitos = matcher.group(1);
		int valor = digitos.length() > 3 ? Integer.MAX_VALUE : Integer.parseInt(digitos);
		return new Quantidade(valor, valor >= 1 && valor <= maximo, restante);
	}

	boolean informada(String texto) {
		return texto != null && !restante.equals(texto.trim());
	}
}
