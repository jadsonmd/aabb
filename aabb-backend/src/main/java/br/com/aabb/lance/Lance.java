package br.com.aabb.lance;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Lance no VAR: jogada de efeito ({@code #canetou}, {@code #chapelou}) ou gol ({@code #marcou}).
 * Só altera a skill depois de {@code aabb.skill.votos-necessarios} reações 👍 de pessoas diferentes.
 *
 * @param messageId    id da mensagem do comando (alvo das reações)
 * @param autor        quem fez a jogada ou marcou o gol
 * @param vitima       quem sofreu a jogada ({@code null} em gols)
 * @param quantidade   número de gols (só para {@code GOL})
 * @param partidaId    partida do dia (só para {@code GOL})
 * @param approvers    ids dos membros que votaram 👍
 * @param informadoPor id do membro que enviou o comando
 */
@Document("events")
public record Lance(
		@Id String id,
		TipoLance tipo,
		StatusLance status,
		String messageId,
		Lado autor,
		Lado vitima,
		int quantidade,
		String partidaId,
		List<String> approvers,
		String informadoPor,
		Instant criadoEm,
		Instant aprovadoEm) {

	public Lance {
		approvers = approvers == null ? List.of() : List.copyOf(approvers);
	}
}
