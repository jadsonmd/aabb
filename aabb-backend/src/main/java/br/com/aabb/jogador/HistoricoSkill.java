package br.com.aabb.jogador;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Auditoria de cada alteração de skill.
 *
 * @param motivo   ex: CANETA, CHAPEU, GOL, VITORIA, EMPATE
 * @param origemId id do lance ou da partida que gerou o ajuste
 */
@Document("historico_skill")
public record HistoricoSkill(
		@Id String id,
		String jogadorId,
		double delta,
		String motivo,
		String origemId,
		Instant em) {
}
