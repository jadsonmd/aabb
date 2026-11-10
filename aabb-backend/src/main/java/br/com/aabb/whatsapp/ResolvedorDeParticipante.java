package br.com.aabb.whatsapp;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.JogadorRepository;

/**
 * Converte JIDs (número ou {@code @lid}) no membro cadastrado e aprende o vínculo LID ↔ número
 * sempre que os dois formatos chegam juntos.
 */
@Component
public class ResolvedorDeParticipante {

	private static final Logger log = LoggerFactory.getLogger(ResolvedorDeParticipante.class);

	private final JogadorRepository jogadores;
	private final MongoTemplate mongo;

	ResolvedorDeParticipante(JogadorRepository jogadores, MongoTemplate mongo) {
		this.jogadores = jogadores;
		this.mongo = mongo;
	}

	/** Resolve um único JID (ex: uma menção). */
	public Optional<Jogador> resolver(Jid jid) {
		return jid == null ? Optional.empty() : resolver(List.of(jid));
	}

	/**
	 * Resolve uma pessoa a partir de todos os JIDs conhecidos dela (ex: {@code participant} e
	 * {@code participantAlt}). Se um deles for número e outro LID, grava o LID no cadastro.
	 */
	public Optional<Jogador> resolver(List<Jid> jids) {
		Optional<Jogador> porNumero = jids.stream()
				.filter(Jid::isNumero)
				.flatMap(jid -> jogadores.findByWhatsappIn(NumeroBrasileiro.variantes(jid.usuario())).stream())
				.findFirst();
		if (porNumero.isPresent()) {
			Jogador jogador = porNumero.get();
			jids.stream()
					.filter(Jid::isLid)
					.filter(lid -> !jogador.lids().contains(lid.usuario()))
					.forEach(lid -> aprender(jogador, lid));
			return porNumero.filter(Jogador::ativo);
		}
		return jids.stream()
				.filter(Jid::isLid)
				.map(jid -> jogadores.findByLids(jid.usuario()))
				.flatMap(Optional::stream)
				.filter(Jogador::ativo)
				.findFirst();
	}

	private void aprender(Jogador jogador, Jid lid) {
		try {
			mongo.updateFirst(
					Query.query(Criteria.where("_id").is(jogador.id())),
					new Update().addToSet("lids", lid.usuario()),
					Jogador.class);
			log.info("LID {} vinculado ao jogador {}", lid, jogador.nomeExibido());
		} catch (DuplicateKeyException e) {
			log.warn("LID {} já está vinculado a outro jogador; vínculo com {} ignorado", lid, jogador.nomeExibido());
		}
	}
}
