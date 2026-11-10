package br.com.aabb.jogador;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Membro cadastrado. Índices (únicos em {@code whatsapp} e {@code lids}) são criados em {@code IndicesMongo}.
 *
 * @param whatsapp número em formato internacional, só dígitos (ex: 5511999999999)
 * @param lids     identificadores {@code @lid} aprendidos automaticamente (só a parte antes do {@code @})
 * @param posicoes posições do jogador; a primeira é a posição principal
 * @param ajustesDePresenca correção manual do admin por ano ("2026" → +3), somada às presenças do sorteio
 */
@Document("jogadores")
public record Jogador(
		@Id String id,
		String nome,
		String apelido,
		String whatsapp,
		List<String> lids,
		List<Posicao> posicoes,
		double skillBase,
		double skill,
		boolean admin,
		boolean ativo,
		Map<String, Integer> ajustesDePresenca,
		Instant criadoEm) {

	public Jogador {
		lids = lids == null ? List.of() : List.copyOf(lids);
		posicoes = posicoes == null ? List.of() : List.copyOf(posicoes);
		ajustesDePresenca = ajustesDePresenca == null ? Map.of() : Map.copyOf(ajustesDePresenca);
	}

	public int ajusteDePresenca(int ano) {
		return ajustesDePresenca.getOrDefault(String.valueOf(ano), 0);
	}

	public int ajusteDePresencaTotal() {
		return ajustesDePresenca.values().stream().mapToInt(Integer::intValue).sum();
	}

	public Posicao posicaoPrincipal() {
		return posicoes.isEmpty() ? null : posicoes.getFirst();
	}

	public boolean goleiro() {
		return posicaoPrincipal() == Posicao.G;
	}

	/** Nome exibido nas mensagens do bot. */
	public String nomeExibido() {
		return apelido != null && !apelido.isBlank() ? apelido : nome;
	}
}
