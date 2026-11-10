package br.com.aabb.jogador;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import br.com.aabb.config.AabbProperties;
import br.com.aabb.whatsapp.Jid;
import br.com.aabb.whatsapp.NumeroBrasileiro;

/**
 * Cadastro automático: quem manda mensagem no grupo sem estar cadastrado vira membro ativo na hora
 * (skill e posição iniciais da configuração, apelido = nome do WhatsApp). O admin pode editar ou desativar
 * depois no painel; desativado não é recadastrado.
 */
@Service
public class CadastroAutomaticoService {

	private static final Logger log = LoggerFactory.getLogger(CadastroAutomaticoService.class);

	private final MongoTemplate mongo;
	private final AabbProperties.Cadastro config;
	private final Clock clock;

	CadastroAutomaticoService(MongoTemplate mongo, AabbProperties properties, Clock clock) {
		this.mongo = mongo;
		this.config = properties.cadastro();
		this.clock = clock;
	}

	/**
	 * Cadastra a pessoa se ela ainda não existir (nem desativada). Sem número de telefone
	 * (só {@code @lid}) não há como cadastrar, e nada é feito.
	 *
	 * @return o membro recém-cadastrado
	 */
	public Optional<Jogador> cadastrarSeNovo(List<Jid> remetente, String nomeNoWhatsApp) {
		Optional<Jid> numero = remetente.stream().filter(Jid::isNumero).findFirst();
		if (numero.isEmpty() || existe(remetente)) {
			return Optional.empty();
		}
		String whatsapp = numero.get().usuario();
		String nome = nomeNoWhatsApp == null || nomeNoWhatsApp.isBlank()
				? "Novo " + whatsapp.substring(Math.max(0, whatsapp.length() - 4))
				: nomeNoWhatsApp.trim();
		String apelido = nome.length() > 30 ? nome.substring(0, 30).trim() : nome;
		List<String> lids = remetente.stream().filter(Jid::isLid).map(Jid::usuario).toList();
		var jogador = new Jogador(null, nome, apelido, whatsapp, lids, List.of(config.posicaoInicial()),
				config.skillInicial(), config.skillInicial(), false, true, Map.of(), clock.instant());
		try {
			var criado = mongo.insert(jogador);
			log.info("Membro {} ({}) cadastrado automaticamente", apelido, whatsapp);
			return Optional.of(criado);
		} catch (DuplicateKeyException e) {
			// Outra mensagem da mesma pessoa criou o cadastro ao mesmo tempo.
			return Optional.empty();
		}
	}

	private boolean existe(List<Jid> remetente) {
		return !buscar(remetente).isEmpty();
	}

	private List<Jogador> buscar(List<Jid> remetente) {
		List<String> numeros = remetente.stream()
				.filter(Jid::isNumero)
				.flatMap(jid -> NumeroBrasileiro.variantes(jid.usuario()).stream())
				.toList();
		List<String> lids = remetente.stream().filter(Jid::isLid).map(Jid::usuario).toList();
		List<Criteria> criterios = new ArrayList<>();
		if (!numeros.isEmpty()) {
			criterios.add(Criteria.where("whatsapp").in(numeros));
		}
		if (!lids.isEmpty()) {
			criterios.add(Criteria.where("lids").in(lids));
		}
		if (criterios.isEmpty()) {
			return List.of();
		}
		return mongo.find(Query.query(new Criteria().orOperator(criterios)), Jogador.class);
	}
}
