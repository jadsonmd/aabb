package br.com.aabb.lance;

import java.util.Set;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import br.com.aabb.comando.Comando;
import br.com.aabb.comando.ComandoHandler;
import br.com.aabb.comando.Contexto;
import br.com.aabb.comando.Mensagens;
import br.com.aabb.config.AabbProperties;
import br.com.aabb.partida.PartidaDoDia;
import br.com.aabb.partida.StatusPartida;
import br.com.aabb.whatsapp.EnviadorWhatsApp;

/**
 * {@code #canetou} / {@code #chapelou}: jogadas de efeito, com as mesmas regras e pesos.
 * Sem autor antes da hashtag, o autor é quem enviou ("#canetou @joao"); "2x" informa a quantidade.
 */
@Component
class JogadaDeEfeitoHandler implements ComandoHandler {

	private final IdentificadorDeLado identificador;
	private final VarService var;
	private final EnviadorWhatsApp enviador;
	private final int votos;
	private final int maximo;

	JogadaDeEfeitoHandler(IdentificadorDeLado identificador, VarService var, EnviadorWhatsApp enviador,
			AabbProperties properties) {
		this.identificador = identificador;
		this.var = var;
		this.enviador = enviador;
		this.votos = properties.skill().votosNecessarios();
		this.maximo = properties.skill().maximoPorLance();
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.CANETOU, Comando.CHAPELOU);
	}

	@Override
	public void executar(Contexto contexto) {
		var mencoes = contexto.mensagem().mencoes();
		// A quantidade ("2x") costuma vir depois da vítima, mas também é aceita antes da hashtag.
		String antes = contexto.comando().antes();
		String depois = contexto.comando().depois();
		Quantidade quantidade = Quantidade.extrair(depois, maximo);
		if (quantidade.informada(depois)) {
			depois = quantidade.restante();
		} else {
			quantidade = Quantidade.extrair(antes, maximo);
			antes = quantidade.restante();
		}
		if (!quantidade.valida()) {
			enviador.enviar(Mensagens.QUANTIDADE_INVALIDA);
			return;
		}
		Lado autor = identificador.autor(antes, mencoes, contexto.membro());
		Lado vitima = identificador.identificar(depois, mencoes);
		if (autor == null || vitima == null) {
			enviador.enviar(Mensagens.USO_JOGADA);
			return;
		}
		if (!autor.identificado() && !vitima.identificado()) {
			enviador.enviar(Mensagens.NINGUEM_IDENTIFICADO);
			return;
		}
		TipoLance tipo = contexto.comando().comando() == Comando.CANETOU ? TipoLance.CANETA : TipoLance.CHAPEU;
		var lance = var.registrar(tipo, contexto.mensagem().messageId(), autor, vitima, quantidade.valor(), null,
				contexto.informadoPor());
		String naoIdentificado = !autor.identificado() ? autor.nome() : !vitima.identificado() ? vitima.nome() : null;
		enviador.enviar(Mensagens.lanceNoVar(VarService.nomeDaJogada(tipo, quantidade.valor()), autor.nome(),
				naoIdentificado, votos), VarService.mencoesNaoIdentificadas(lance));
	}
}

/** {@code @jogador #marcou 2x} ou {@code #marcou 2x} (gols de quem enviou): também aprovados pelo VAR. */
@Component
class MarcouHandler implements ComandoHandler {

	private final PartidaDoDia partidaDoDia;
	private final IdentificadorDeLado identificador;
	private final VarService var;
	private final MongoTemplate mongo;
	private final EnviadorWhatsApp enviador;
	private final AabbProperties.Skill config;

	MarcouHandler(PartidaDoDia partidaDoDia, IdentificadorDeLado identificador, VarService var, MongoTemplate mongo,
			EnviadorWhatsApp enviador, AabbProperties properties) {
		this.partidaDoDia = partidaDoDia;
		this.identificador = identificador;
		this.var = var;
		this.mongo = mongo;
		this.enviador = enviador;
		this.config = properties.skill();
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.MARCOU);
	}

	@Override
	public void executar(Contexto contexto) {
		var partida = partidaDoDia.obter(contexto).orElse(null);
		if (partida == null) {
			return;
		}
		if (partida.status() == StatusPartida.ABERTA || partida.sorteioVigente().isEmpty()) {
			enviador.enviar(Mensagens.SEM_SORTEIO);
			return;
		}
		// "#marcou 2x" (o número sem "x" também é aceito); sem quantidade, vale 1 gol.
		Quantidade quantidade = Quantidade.extrair(contexto.comando().depois(), config.maximoPorLance());
		if (!quantidade.valida()) {
			enviador.enviar(Mensagens.QUANTIDADE_INVALIDA);
			return;
		}
		Lado autor = identificador.autor(contexto.comando().antes(), contexto.mensagem().mencoes(),
				contexto.membro());
		boolean noJogo = autor.identificado()
				&& partida.sorteioVigente().orElseThrow().membros().contains(autor.jogadorId());
		if (!noJogo) {
			enviador.enviar(Mensagens.golsNaoComputados(autor.nome()));
			return;
		}
		Lance aprovado = mongo.findOne(Query.query(Criteria.where("tipo").is(TipoLance.GOL)
				.and("partidaId").is(partida.id())
				.and("autor.jogadorId").is(autor.jogadorId())
				.and("status").is(StatusLance.APPROVED)), Lance.class);
		if (aprovado != null) {
			enviador.enviar(Mensagens.golsJaRegistrados(autor.nome(), aprovado.quantidade()));
			return;
		}
		var.registrar(TipoLance.GOL, contexto.mensagem().messageId(), autor, null, quantidade.valor(), partida.id(),
				contexto.informadoPor());
		enviador.enviar(Mensagens.golsNoVar(quantidade.valor(), autor.nome(), config.votosNecessarios()));
	}
}
