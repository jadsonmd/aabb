package br.com.aabb.lance;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import br.com.aabb.comando.Mensagens;
import br.com.aabb.config.AabbProperties;
import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.JogadorRepository;
import br.com.aabb.whatsapp.EnviadorWhatsApp;
import br.com.aabb.whatsapp.Jid;
import br.com.aabb.whatsapp.MensagemRecebida;

/**
 * VAR: registra lances pendentes e contabiliza os votos (reações 👍) até a aprovação.
 */
@Service
public class VarService {

	private final MongoTemplate mongo;
	private final AprovacaoService aprovacao;
	private final JogadorRepository jogadores;
	private final EnviadorWhatsApp enviador;
	private final AabbProperties.Skill config;
	private final Clock clock;

	VarService(MongoTemplate mongo, AprovacaoService aprovacao, JogadorRepository jogadores,
			EnviadorWhatsApp enviador, AabbProperties properties, Clock clock) {
		this.mongo = mongo;
		this.aprovacao = aprovacao;
		this.jogadores = jogadores;
		this.enviador = enviador;
		this.config = properties.skill();
		this.clock = clock;
	}

	Lance registrar(TipoLance tipo, String messageId, Lado autor, Lado vitima, int quantidade, String partidaId,
			String informadoPor) {
		return mongo.insert(new Lance(null, tipo, StatusLance.PENDING, messageId, autor, vitima, quantidade,
				partidaId, List.of(), informadoPor, clock.instant(), null));
	}

	/** Registra (ou retira) o voto de um membro e aprova o lance ao atingir os votos necessários. */
	public void votar(MensagemRecebida.Reacao reacao, Jogador votante) {
		if (reacao.alvoMessageId() == null) {
			return;
		}
		var pendente = Query.query(Criteria.where("messageId").is(reacao.alvoMessageId())
				.and("status").is(StatusLance.PENDING)
				.and("autor.jogadorId").ne(votante.id()));
		var voto = reacao.positiva()
				? new Update().addToSet("approvers", votante.id())
				: new Update().pull("approvers", votante.id());
		if (mongo.updateFirst(pendente, voto, Lance.class).getMatchedCount() == 0 || !reacao.positiva()) {
			return;
		}
		try {
			aprovacao.aprovarSePossivel(reacao.alvoMessageId()).ifPresent(this::anunciarAprovacao);
		} catch (DuplicateKeyException e) {
			descartarGolRepetido(reacao.alvoMessageId());
		}
	}

	private void anunciarAprovacao(Lance lance) {
		if (lance.tipo() == TipoLance.GOL) {
			enviador.enviar(Mensagens.golsAprovados(lance.quantidade(), lance.autor().nome(),
					config.gol() * lance.quantidade()));
			return;
		}
		var texto = new StringBuilder(Mensagens.lanceAprovado(nomeDaJogada(lance.tipo(), lance.quantidade()),
				lance.autor().nome(),
				lance.vitima() == null ? null : lance.vitima().nome()));
		List<String> skills = new ArrayList<>();
		jogadores.findAllById(AprovacaoService.identificados(lance))
				.forEach(j -> skills.add(Mensagens.skillAtual(j.nomeExibido(), j.skill())));
		if (!skills.isEmpty()) {
			texto.append("\nSkill: ").append(String.join(" · ", skills));
		}
		enviador.enviar(texto.toString(), mencoesNaoIdentificadas(lance));
	}

	private void descartarGolRepetido(String messageId) {
		Lance lance = mongo.findAndModify(
				Query.query(Criteria.where("messageId").is(messageId).and("status").is(StatusLance.PENDING)),
				new Update().set("status", StatusLance.DESCARTADO), Lance.class);
		if (lance == null) {
			return;
		}
		Lance aprovado = mongo.findOne(Query.query(Criteria.where("tipo").is(TipoLance.GOL)
				.and("partidaId").is(lance.partidaId())
				.and("autor.jogadorId").is(lance.autor().jogadorId())
				.and("status").is(StatusLance.APPROVED)), Lance.class);
		if (aprovado != null) {
			enviador.enviar(Mensagens.golsJaRegistrados(lance.autor().nome(), aprovado.quantidade()));
		}
	}

	/** "Caneta" para uma, "3 canetas" para várias. */
	static String nomeDaJogada(TipoLance tipo, int quantidade) {
		if (quantidade <= 1) {
			return switch (tipo) {
				case CANETA -> "Caneta";
				case CHAPEU -> "Chapéu";
				case GOL -> "Gol";
			};
		}
		return quantidade + switch (tipo) {
			case CANETA -> " canetas";
			case CHAPEU -> " chapéus";
			case GOL -> " gols";
		};
	}

	/** Menções por {@code @lid} desconhecido viram nome clicável no WhatsApp. */
	static List<Jid> mencoesNaoIdentificadas(Lance lance) {
		List<Jid> mencoes = new ArrayList<>();
		for (Lado lado : new Lado[] { lance.autor(), lance.vitima() }) {
			if (lado != null && !lado.identificado() && lado.jid() != null) {
				mencoes.add(Jid.of(lado.jid()));
			}
		}
		return mencoes;
	}
}
