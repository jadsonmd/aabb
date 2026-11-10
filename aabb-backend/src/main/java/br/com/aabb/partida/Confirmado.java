package br.com.aabb.partida;

import java.time.Instant;

/**
 * Entrada da lista do dia, em ordem de confirmação.
 *
 * @param jogadorId        id do membro; {@code null} para convidados
 * @param nome             nome exibido (apelido do membro ou nome do convidado)
 * @param nomeNormalizado  nome em minúsculas, sem acentos e espaços extras (convidados)
 * @param convidadoPor     id do membro que convidou (convidados)
 * @param convidadoPorNome nome exibido de quem convidou (convidados)
 */
public record Confirmado(
		TipoConfirmado tipo,
		String jogadorId,
		String nome,
		String nomeNormalizado,
		String convidadoPor,
		String convidadoPorNome,
		Instant confirmadoEm) {

	public static Confirmado membro(String jogadorId, String nome, Instant em) {
		return new Confirmado(TipoConfirmado.MEMBRO, jogadorId, nome, null, null, null, em);
	}

	public static Confirmado convidado(String nome, String convidadoPor, String convidadoPorNome, Instant em) {
		return new Confirmado(TipoConfirmado.CONVIDADO, null, nome, Nomes.normalizar(nome), convidadoPor,
				convidadoPorNome, em);
	}

	public boolean membro() {
		return tipo == TipoConfirmado.MEMBRO;
	}
}
