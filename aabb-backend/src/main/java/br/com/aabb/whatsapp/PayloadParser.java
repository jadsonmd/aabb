package br.com.aabb.whatsapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Converte o payload do webhook da Evolution API v2 em {@link MensagemRecebida}.
 */
@Component
public class PayloadParser {

	public Optional<MensagemRecebida> parse(JsonNode payload) {
		String evento = payload.path("event").asString("");
		if (!evento.isEmpty() && !evento.equalsIgnoreCase("messages.upsert")) {
			return Optional.empty();
		}
		JsonNode data = payload.path("data");
		JsonNode key = data.path("key");
		String messageId = texto(key, "id");
		if (messageId == null) {
			return Optional.empty();
		}

		List<Jid> remetente = new ArrayList<>();
		adicionar(remetente, texto(key, "participant"));
		adicionar(remetente, texto(key, "participantAlt"));
		Jid grupo = Jid.of(texto(key, "remoteJid"));
		if (remetente.isEmpty()) {
			// Conversa privada: o remetente é o próprio chat.
			adicionar(remetente, texto(key, "remoteJid"));
			adicionar(remetente, texto(key, "remoteJidAlt"));
		}

		JsonNode message = data.path("message");
		MensagemRecebida.Reacao reacao = null;
		JsonNode reactionMessage = message.path("reactionMessage");
		if (reactionMessage.isObject()) {
			reacao = new MensagemRecebida.Reacao(
					texto(reactionMessage.path("key"), "id"),
					reactionMessage.path("text").asString(""));
		}

		String conteudo = Objects.requireNonNullElse(texto(message, "conversation"),
				Objects.requireNonNullElse(texto(message.path("extendedTextMessage"), "text"), ""));

		// A Evolution API v2 envia as menções em data.contextInfo; versões antigas, dentro do extendedTextMessage.
		List<Jid> mencoes = new ArrayList<>();
		for (JsonNode origem : List.of(data.path("contextInfo"), message.path("extendedTextMessage").path("contextInfo"))) {
			for (JsonNode mencao : origem.path("mentionedJid")) {
				adicionar(mencoes, mencao.asString(null));
			}
		}

		return Optional.of(new MensagemRecebida(
				messageId,
				grupo,
				List.copyOf(remetente),
				texto(data, "pushName"),
				key.path("fromMe").asBoolean(false),
				conteudo,
				List.copyOf(mencoes),
				reacao));
	}

	private static String texto(JsonNode node, String campo) {
		JsonNode valor = node.path(campo);
		return valor.isString() && !valor.asString().isBlank() ? valor.asString() : null;
	}

	private static void adicionar(List<Jid> jids, String bruto) {
		Jid jid = Jid.of(bruto);
		if (jid != null && !jids.contains(jid)) {
			jids.add(jid);
		}
	}
}
