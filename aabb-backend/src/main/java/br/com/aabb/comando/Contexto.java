package br.com.aabb.comando;

import java.time.LocalDate;
import java.util.Optional;

import br.com.aabb.jogador.Jogador;
import br.com.aabb.whatsapp.MensagemRecebida;

/**
 * Tudo que um handler precisa para executar um comando.
 *
 * @param remetente membro que enviou (vazio se não identificado)
 * @param hoje      data de hoje no fuso do clube
 */
public record Contexto(MensagemRecebida mensagem, ComandoRecebido comando, Optional<Jogador> remetente, LocalDate hoje) {

	public Jogador membro() {
		return remetente.orElseThrow();
	}

	/** Identificação de quem enviou, para auditoria (id do membro ou JID). */
	public String informadoPor() {
		return remetente.map(Jogador::id)
				.orElseGet(() -> mensagem.remetente().isEmpty() ? null : mensagem.remetente().getFirst().completo());
	}
}
