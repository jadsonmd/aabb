package br.com.aabb.whatsapp;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/**
 * Recebe os webhooks da Evolution API. Só é acessível dentro da rede {@code aabb-network}.
 */
@RestController
@RequestMapping("/api/whatsapp/webhook")
class WebhookController {

	private final WebhookService webhookService;

	WebhookController(WebhookService webhookService) {
		this.webhookService = webhookService;
	}

	@PostMapping
	ResponseEntity<Void> receber(@RequestBody JsonNode payload) {
		webhookService.receber(payload);
		return ResponseEntity.ok().build();
	}
}
