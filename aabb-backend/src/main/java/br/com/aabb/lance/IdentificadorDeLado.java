package br.com.aabb.lance;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import br.com.aabb.jogador.Jogador;
import br.com.aabb.partida.Nomes;
import br.com.aabb.whatsapp.Jid;
import br.com.aabb.whatsapp.ResolvedorDeParticipante;

/**
 * Identifica um lado de um lance a partir do trecho de texto: menção ({@code @5511...} ou {@code @lid})
 * resolvida para o membro, ou nome em texto livre (convidado, sem busca no cadastro).
 */
@Component
class IdentificadorDeLado {

	private static final Pattern MENCAO = Pattern.compile("@(\\d+)");

	private final ResolvedorDeParticipante resolvedor;

	IdentificadorDeLado(ResolvedorDeParticipante resolvedor) {
		this.resolvedor = resolvedor;
	}

	/** @return {@code null} se o trecho estiver vazio */
	Lado identificar(String trecho, List<Jid> mencoes) {
		if (trecho == null || trecho.isBlank()) {
			return null;
		}
		var matcher = MENCAO.matcher(trecho);
		if (matcher.find()) {
			String usuario = matcher.group(1);
			Jid jid = mencoes.stream()
					.filter(m -> m.usuario().equals(usuario))
					.findFirst()
					.orElse(Jid.numero(usuario));
			return resolvedor.resolver(jid)
					.map(j -> new Lado(j.id(), jid.completo(), j.nomeExibido()))
					.orElseGet(() -> new Lado(null, jid.completo(), "@" + usuario));
		}
		String nome = Nomes.limpar(trecho.replace("@", ""));
		return nome.isEmpty() ? null : new Lado(null, null, nome);
	}

	/**
	 * Autor do lance. O WhatsApp não permite mencionar a si mesmo, então trecho vazio ou "eu" significa
	 * que o autor é quem enviou a mensagem (ex: "#canetou @joao", "eu #marcou 2").
	 */
	Lado autor(String trecho, List<Jid> mencoes, Jogador remetente) {
		if (trecho == null || trecho.isBlank() || trecho.trim().equalsIgnoreCase("eu")) {
			return membro(remetente);
		}
		return identificar(trecho, mencoes);
	}

	static Lado membro(Jogador jogador) {
		return new Lado(jogador.id(), null, jogador.nomeExibido());
	}
}
