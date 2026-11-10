package br.com.aabb.fluxo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.mongodb.MongoDBContainer;

import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.JogadorRequest;
import br.com.aabb.jogador.JogadorService;
import br.com.aabb.jogador.Posicao;
import br.com.aabb.whatsapp.EnviadorWhatsApp;
import br.com.aabb.whatsapp.Jid;
import br.com.aabb.whatsapp.WebhookService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base dos testes de ponta a ponta: MongoDB real em replica set (Testcontainers), relógio controlado
 * e um enviador falso que guarda as mensagens que o bot mandaria ao grupo.
 */
@SpringBootTest(properties = {
		"aabb.grupo-jid=" + TesteDeIntegracao.GRUPO,
		"aabb.agenda.abertura-cron=-" })
@Import(TesteDeIntegracao.Config.class)
abstract class TesteDeIntegracao {

	static final String GRUPO = "120363000000000000@g.us";
	static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
	/** Segunda-feira, dia de jogo. */
	static final LocalDateTime SEGUNDA_10H = LocalDateTime.of(2026, 11, 9, 10, 0);

	@ServiceConnection
	static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0").withReplicaSet();

	static {
		MONGO.start();
	}

	@Autowired
	WebhookService webhook;
	@Autowired
	JogadorService jogadores;
	@Autowired
	MongoTemplate mongo;
	@Autowired
	RelogioDeTeste relogio;
	@Autowired
	EnviadorFalso enviador;

	private final JsonMapper json = JsonMapper.builder().build();
	private final AtomicInteger sequencia = new AtomicInteger();

	@BeforeEach
	void limpar() {
		for (String colecao : List.of("partidas", "jogadores", "events", "presencas", "historico_skill",
				"mensagens_processadas", "webhooks_brutos")) {
			mongo.remove(new Query(), colecao);
		}
		relogio.definir(SEGUNDA_10H);
		enviador.mensagens.clear();
	}

	// ---------- cadastro ----------

	Jogador membro(String apelido, String whatsapp, double skill, Posicao... posicoes) {
		return jogadores.criar(new JogadorRequest(apelido + " Silva", apelido, whatsapp, List.of(posicoes), skill, false, null, null));
	}

	Jogador admin(String apelido, String whatsapp, double skill, Posicao... posicoes) {
		return jogadores.criar(new JogadorRequest(apelido + " Silva", apelido, whatsapp, List.of(posicoes), skill, true, null, null));
	}

	Jogador recarregar(Jogador jogador) {
		return jogadores.buscar(jogador.id());
	}

	// ---------- envio de mensagens ----------

	/** Envia um texto ao grupo como o jogador; devolve o id da mensagem. */
	String texto(Jogador de, String texto, String... mencoes) {
		return texto(Jid.numero(de.whatsapp()).completo(), null, texto, mencoes);
	}

	String texto(String participant, String participantAlt, String texto, String... mencoes) {
		return textoComNome(participant, participantAlt, null, texto, mencoes);
	}

	/** Mensagem com o nome que a pessoa usa no WhatsApp ({@code pushName}). */
	String textoComNome(String participant, String participantAlt, String pushName, String texto, String... mencoes) {
		String id = "MSG" + sequencia.incrementAndGet();
		var mentioned = json.createArrayNode();
		for (String mencao : mencoes) {
			mentioned.add(mencao);
		}
		var payload = json.createObjectNode();
		payload.put("event", "messages.upsert");
		var data = payload.putObject("data");
		var key = data.putObject("key");
		key.put("remoteJid", GRUPO);
		key.put("participant", participant);
		if (participantAlt != null) {
			key.put("participantAlt", participantAlt);
		}
		key.put("fromMe", false);
		key.put("id", id);
		if (pushName != null) {
			data.put("pushName", pushName);
		}
		var extended = data.putObject("message").putObject("extendedTextMessage");
		extended.put("text", texto);
		extended.putObject("contextInfo").set("mentionedJid", mentioned);
		webhook.receber(payload);
		return id;
	}

	void reagir(Jogador de, String messageId, String emoji) {
		var payload = json.createObjectNode();
		payload.put("event", "messages.upsert");
		var data = payload.putObject("data");
		var key = data.putObject("key");
		key.put("remoteJid", GRUPO);
		key.put("participant", Jid.numero(de.whatsapp()).completo());
		key.put("id", "REACAO" + sequencia.incrementAndGet());
		var reacao = data.putObject("message").putObject("reactionMessage");
		reacao.putObject("key").put("id", messageId);
		reacao.put("text", emoji);
		webhook.receber(payload);
	}

	static String mencao(Jogador jogador) {
		return Jid.numero(jogador.whatsapp()).completo();
	}

	String ultimaMensagem() {
		return enviador.mensagens.getLast();
	}

	// ---------- infraestrutura de teste ----------

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		RelogioDeTeste relogioDeTeste() {
			return new RelogioDeTeste();
		}

		@Bean
		@Primary
		EnviadorFalso enviadorFalso() {
			return new EnviadorFalso();
		}
	}

	static class RelogioDeTeste extends Clock {

		private volatile Instant agora = Instant.now();

		void definir(LocalDateTime dataHora) {
			agora = dataHora.atZone(ZONA).toInstant();
		}

		@Override
		public ZoneId getZone() {
			return ZONA;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(agora, zone);
		}

		@Override
		public Instant instant() {
			return agora;
		}
	}

	static class EnviadorFalso implements EnviadorWhatsApp {

		final List<String> mensagens = Collections.synchronizedList(new ArrayList<>());

		@Override
		public void enviar(String texto, List<Jid> mencoes) {
			mensagens.add(texto);
		}
	}
}
