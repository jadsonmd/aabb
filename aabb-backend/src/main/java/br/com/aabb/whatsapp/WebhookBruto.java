package br.com.aabb.whatsapp;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cópia bruta de cada webhook recebido, mantida por 7 dias para diagnóstico
 * e para gerar fixtures de teste a partir de payloads reais.
 */
@Document("webhooks_brutos")
public record WebhookBruto(
		@Id String id,
		String json,
		Instant recebidoEm) {
}
