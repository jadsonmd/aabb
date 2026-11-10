package br.com.aabb.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class PayloadParserTest {

	private final PayloadParser parser = new PayloadParser();
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void extraiTextoMencoesERemetenteComLid() {
		var payload = json.readTree("""
				{"event":"messages.upsert","instance":"aabb-bot","data":{
				  "key":{"remoteJid":"120363000000000000@g.us","participant":"123456789012345@lid",
				         "participantAlt":"5511933333333@s.whatsapp.net","fromMe":false,"id":"BAE5X"},
				  "pushName":"Jadson",
				  "message":{"extendedTextMessage":{"text":"@5511900000000 #canetou @987654321098765",
				    "contextInfo":{"mentionedJid":["5511900000000@s.whatsapp.net","987654321098765@lid"]}}}}}
				""");

		var mensagem = parser.parse(payload).orElseThrow();

		assertThat(mensagem.messageId()).isEqualTo("BAE5X");
		assertThat(mensagem.grupo().completo()).isEqualTo("120363000000000000@g.us");
		assertThat(mensagem.remetente()).containsExactly(Jid.of("123456789012345@lid"),
				Jid.of("5511933333333@s.whatsapp.net"));
		assertThat(mensagem.texto()).isEqualTo("@5511900000000 #canetou @987654321098765");
		assertThat(mensagem.mencoes()).hasSize(2);
		assertThat(mensagem.isReacao()).isFalse();
	}

	@Test
	void extraiMencoesDoContextInfoNoFormatoRealDaEvolutionV2() {
		// Estrutura capturada de um webhook real (números trocados).
		var payload = json.readTree("""
				{"event":"messages.upsert","instance":"aabb-bot","data":{
				  "key":{"remoteJid":"120363000000000000@g.us","fromMe":false,"id":"3AC4",
				         "participant":"100000000000001@lid","participantAlt":"554899990000@s.whatsapp.net",
				         "addressingMode":"lid"},
				  "messageType":"conversation",
				  "message":{"messageContextInfo":{"threadId":[]},"conversation":"@111222333 #canetou @200000000000002"},
				  "contextInfo":{"mentionedJid":["111222333@lid","200000000000002@lid"],"groupMentions":[]}}}
				""");

		var mensagem = parser.parse(payload).orElseThrow();

		assertThat(mensagem.texto()).isEqualTo("@111222333 #canetou @200000000000002");
		assertThat(mensagem.mencoes()).containsExactly(Jid.of("111222333@lid"), Jid.of("200000000000002@lid"));
		assertThat(mensagem.remetente()).containsExactly(Jid.of("100000000000001@lid"),
				Jid.of("554899990000@s.whatsapp.net"));
	}

	@Test
	void extraiReacaoERemocaoDeReacao() {
		var payload = json.readTree("""
				{"event":"messages.upsert","data":{"key":{"remoteJid":"g@g.us","participant":"5511922222222@s.whatsapp.net","id":"R1"},
				 "message":{"reactionMessage":{"key":{"id":"BAE5X"},"text":"👍🏽"}}}}
				""");
		var reacao = parser.parse(payload).orElseThrow().reacao();
		assertThat(reacao.alvoMessageId()).isEqualTo("BAE5X");
		assertThat(reacao.positiva()).isTrue();

		var remocao = json.readTree("""
				{"data":{"key":{"remoteJid":"g@g.us","participant":"5511922222222@s.whatsapp.net","id":"R2"},
				 "message":{"reactionMessage":{"key":{"id":"BAE5X"},"text":""}}}}
				""");
		assertThat(parser.parse(remocao).orElseThrow().reacao().positiva()).isFalse();
	}

	@Test
	void ignoraOutrosEventos() {
		var payload = json.readTree("""
				{"event":"connection.update","data":{"state":"open"}}
				""");
		assertThat(parser.parse(payload)).isEmpty();
	}
}
