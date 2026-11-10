package br.com.aabb.whatsapp;

import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.com.aabb.config.AabbProperties;

/**
 * Cliente HTTP da Evolution API v2 ({@code POST /message/sendText/{instancia}}).
 */
@Component
class EvolutionApiClient {

	private final RestClient restClient;
	private final AabbProperties.Evolution evolution;

	EvolutionApiClient(RestClient.Builder builder, AabbProperties properties) {
		this.evolution = properties.evolution();
		this.restClient = builder
				.baseUrl(evolution.url())
				.defaultHeader("apikey", evolution.apiKey())
				.build();
	}

	@Retryable(maxRetries = 3, delay = 1000, multiplier = 2)
	void sendText(String destino, String texto, List<Jid> mencoes) {
		var body = new HashMap<String, Object>();
		body.put("number", destino);
		body.put("text", texto);
		body.put("delay", evolution.delayMs());
		body.put("linkPreview", false);
		// A Evolution API rejeita "mentioned" vazio (400): só envia quando houver menção.
		if (!mencoes.isEmpty()) {
			body.put("mentioned", mencoes.stream().map(Jid::completo).toList());
		}
		restClient.post()
				.uri("/message/sendText/{instancia}", evolution.instancia())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity();
	}
}

@Component
class EvolutionEnviadorWhatsApp implements EnviadorWhatsApp {

	private static final Logger log = LoggerFactory.getLogger(EvolutionEnviadorWhatsApp.class);

	private final EvolutionApiClient client;
	private final AabbProperties properties;

	EvolutionEnviadorWhatsApp(EvolutionApiClient client, AabbProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public void enviar(String texto, List<Jid> mencoes) {
		if (!properties.grupoConfigurado()) {
			log.warn("Grupo não configurado (aabb.grupo-jid); mensagem não enviada: {}", texto);
			return;
		}
		try {
			client.sendText(properties.grupoJid(), texto, mencoes);
		} catch (RuntimeException e) {
			log.error("Falha ao enviar mensagem ao grupo após tentativas: {}", texto, e);
		}
	}
}
