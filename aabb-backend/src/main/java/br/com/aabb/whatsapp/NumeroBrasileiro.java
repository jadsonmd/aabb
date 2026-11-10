package br.com.aabb.whatsapp;

import java.util.List;

/**
 * Celulares brasileiros ganharam o nono dígito, mas o WhatsApp ainda identifica muitas contas antigas
 * sem ele: o cadastro pode ter 5548999990011 e o webhook chegar com 554899990011. Os dois formatos
 * representam o mesmo número.
 */
public final class NumeroBrasileiro {

	private NumeroBrasileiro() {
	}

	/** O número como veio e, se for celular brasileiro, também com/sem o nono dígito. */
	public static List<String> variantes(String numero) {
		if (numero == null || !numero.startsWith("55")) {
			return numero == null ? List.of() : List.of(numero);
		}
		String ddd = numero.substring(0, 4);
		String assinante = numero.substring(4);
		if (numero.length() == 13 && assinante.startsWith("9")) {
			return List.of(numero, ddd + assinante.substring(1));
		}
		if (numero.length() == 12 && "6789".indexOf(assinante.charAt(0)) >= 0) {
			return List.of(numero, ddd + "9" + assinante);
		}
		return List.of(numero);
	}
}
