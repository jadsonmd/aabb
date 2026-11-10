package br.com.aabb.whatsapp;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import br.com.aabb.config.AabbProperties;
import tools.jackson.databind.JsonNode;

/**
 * Ponto de entrada dos webhooks: guarda o payload bruto, filtra o grupo oficial,
 * garante que cada mensagem seja processada uma única vez e repassa ao roteador de comandos.
 */
@Service
public class WebhookService {

	private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

	private final MongoTemplate mongo;
	private final PayloadParser parser;
	private final ProcessadorDeMensagem processador;
	private final AabbProperties properties;
	private final Clock clock;

	WebhookService(MongoTemplate mongo, PayloadParser parser, ProcessadorDeMensagem processador,
			AabbProperties properties, Clock clock) {
		this.mongo = mongo;
		this.parser = parser;
		this.processador = processador;
		this.properties = properties;
		this.clock = clock;
	}

	public void receber(JsonNode payload) {
		Instant agora = clock.instant();
		mongo.insert(new WebhookBruto(null, payload.toString(), agora));

		var mensagem = parser.parse(payload).orElse(null);
		if (mensagem == null || mensagem.fromMe()) {
			return;
		}
		if (!properties.grupoConfigurado()) {
			log.info("Webhook recebido do chat {} (remetente {}). Configure AABB_GRUPO_JID para processar comandos.",
					mensagem.grupo(), mensagem.remetente());
			return;
		}
		if (mensagem.grupo() == null || !mensagem.grupo().completo().equals(properties.grupoJid())) {
			return;
		}
		if (!registrarProcessamento(mensagem.messageId(), agora)) {
			log.debug("Mensagem {} já processada; reenvio ignorado.", mensagem.messageId());
			return;
		}
		try {
			processador.processar(mensagem);
		} catch (RuntimeException e) {
			// Libera a mensagem para que o reenvio automático da Evolution API tente de novo.
			mongo.remove(Query.query(Criteria.where("_id").is(mensagem.messageId())), MensagemProcessada.class);
			throw e;
		}
	}

	private boolean registrarProcessamento(String messageId, Instant agora) {
		try {
			mongo.insert(new MensagemProcessada(messageId, agora));
			return true;
		} catch (DuplicateKeyException e) {
			return false;
		}
	}
}
