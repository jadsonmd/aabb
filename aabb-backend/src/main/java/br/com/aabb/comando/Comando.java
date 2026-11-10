package br.com.aabb.comando;

import java.util.Arrays;
import java.util.Optional;

/**
 * Hashtags reconhecidas pelo bot.
 *
 * @param admin                exclusivo de administradores
 * @param soDiaDeJogo          atua na partida do dia (ignorado em silêncio nos outros dias)
 * @param exigeIdentificacao   o remetente precisa ser um membro cadastrado
 */
public enum Comando {
	VOU("vou", false, true, true),
	CONVIDADO("convidado", false, true, true),
	DESCONVIDAR("desconvidar", false, true, true),
	DM("dm", false, true, true),
	LISTA("lista", false, true, true),
	SORTEAR("sortear", true, true, true),
	RESSORTEAR("ressortear", true, true, true),
	ABRIRLISTA("abrirlista", true, true, true),
	JOGOCANCELADO("jogocancelado", true, true, true),
	TIMEAZULGANHOU("timeazulganhou", false, true, false),
	TIMEAMARELOGANHOU("timeamareloganhou", false, true, false),
	EMPATE("empate", false, true, false),
	MARCOU("marcou", false, true, true),
	CANETOU("canetou", false, false, true),
	CHAPELOU("chapelou", false, false, true),
	RANKING("ranking", false, false, true),
	PRESENCA("presenca", false, false, true),
	TITULOS("titulos", false, false, true);

	private final String hashtag;
	private final boolean admin;
	private final boolean soDiaDeJogo;
	private final boolean exigeIdentificacao;

	Comando(String hashtag, boolean admin, boolean soDiaDeJogo, boolean exigeIdentificacao) {
		this.hashtag = hashtag;
		this.admin = admin;
		this.soDiaDeJogo = soDiaDeJogo;
		this.exigeIdentificacao = exigeIdentificacao;
	}

	public static Optional<Comando> daHashtag(String hashtag) {
		return Arrays.stream(values()).filter(c -> c.hashtag.equals(hashtag)).findFirst();
	}

	public String hashtag() {
		return hashtag;
	}

	public boolean admin() {
		return admin;
	}

	public boolean soDiaDeJogo() {
		return soDiaDeJogo;
	}

	public boolean exigeIdentificacao() {
		return exigeIdentificacao;
	}
}
