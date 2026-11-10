package br.com.aabb.partida;

import java.text.Normalizer;
import java.util.Locale;

public final class Nomes {

	private Nomes() {
	}

	/** Minúsculas, sem acentos e com espaços simples: "  Marcélo  Silva " → "marcelo silva". */
	public static String normalizar(String nome) {
		String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return semAcento.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/** Remove espaços extras mantendo a grafia: "  marcelo   Silva " → "marcelo Silva". */
	public static String limpar(String nome) {
		return nome.trim().replaceAll("\\s+", " ");
	}
}
