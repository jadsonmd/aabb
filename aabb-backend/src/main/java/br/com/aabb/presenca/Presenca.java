package br.com.aabb.presenca;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Presença de um membro em uma partida (único por {@code jogadorId + partidaId}).
 */
@Document("presencas")
public record Presenca(@Id String id, String jogadorId, String partidaId, String data, Instant criadoEm) {
}
