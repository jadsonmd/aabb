package br.com.aabb.partida;

import java.util.Optional;

import org.springframework.stereotype.Component;

import br.com.aabb.comando.Contexto;
import br.com.aabb.comando.Mensagens;
import br.com.aabb.whatsapp.EnviadorWhatsApp;

/**
 * Carrega a partida de hoje para um comando de lista, respondendo no grupo quando ela ainda não
 * foi aberta ou foi cancelada.
 */
@Component
public class PartidaDoDia {

	private final PartidaService partidas;
	private final EnviadorWhatsApp enviador;

	PartidaDoDia(PartidaService partidas, EnviadorWhatsApp enviador) {
		this.partidas = partidas;
		this.enviador = enviador;
	}

	public Optional<Partida> obter(Contexto contexto) {
		var partida = partidas.doDia(contexto.hoje());
		if (partida.isEmpty()) {
			enviador.enviar(Mensagens.LISTA_NAO_ABERTA);
			return Optional.empty();
		}
		if (partida.get().status() == StatusPartida.CANCELADA) {
			enviador.enviar(Mensagens.CANCELADO);
			return Optional.empty();
		}
		return partida;
	}
}
