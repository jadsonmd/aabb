package br.com.aabb.whatsapp;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de idempotência: o {@code id} é o id da mensagem do WhatsApp.
 */
@Document("mensagens_processadas")
public record MensagemProcessada(
		@Id String id,
		Instant processadaEm) {
}
