package br.com.aabb.whatsapp;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.aabb.config.AabbProperties;

class EvolutionApiClientTest {

	private MockRestServiceServer servidor;
	private EvolutionApiClient client;

	@BeforeEach
	void configurar() {
		var builder = RestClient.builder();
		servidor = MockRestServiceServer.bindTo(builder).build();
		var properties = new AabbProperties("g@g.us",
				new AabbProperties.Evolution("http://evolution", "chave", "aabb-bot", 2000), null, null, null, null);
		client = new EvolutionApiClient(builder, properties);
	}

	@Test
	void naoEnviaMentionedVazioPorqueAEvolutionRejeita() {
		servidor.expect(requestTo("http://evolution/message/sendText/aabb-bot"))
				.andExpect(header("apikey", "chave"))
				.andExpect(content().json("""
						{"number":"g@g.us","text":"oi","delay":2000,"linkPreview":false}
						""", JsonCompareMode.STRICT))
				.andRespond(withSuccess());

		client.sendText("g@g.us", "oi", List.of());

		servidor.verify();
	}

	@Test
	void enviaMencoesQuandoHouver() {
		servidor.expect(requestTo("http://evolution/message/sendText/aabb-bot"))
				.andExpect(content().json("""
						{"mentioned":["123@lid"]}
						"""))
				.andRespond(withSuccess());

		client.sendText("g@g.us", "@123", List.of(Jid.of("123@lid")));

		servidor.verify();
	}
}
