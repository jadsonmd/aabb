package br.com.aabb.resultado;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import br.com.aabb.comando.Comando;
import br.com.aabb.comando.ComandoHandler;
import br.com.aabb.comando.Contexto;
import br.com.aabb.comando.Mensagens;
import br.com.aabb.config.AabbProperties;
import br.com.aabb.partida.PartidaDoDia;
import br.com.aabb.partida.PartidaService;
import br.com.aabb.partida.Resultado;
import br.com.aabb.partida.StatusPartida;
import br.com.aabb.sorteio.Escalado;
import br.com.aabb.sorteio.Time;
import br.com.aabb.whatsapp.EnviadorWhatsApp;

/**
 * {@code #timeazulganhou}, {@code #timeamareloganhou} e {@code #empate}: qualquer pessoa do grupo
 * pode enviar; vale o primeiro, e o resultado é definitivo.
 */
@Component
class ResultadoHandler implements ComandoHandler {

	private final PartidaDoDia partidaDoDia;
	private final PartidaService partidas;
	private final ResultadoService resultados;
	private final EnviadorWhatsApp enviador;
	private final AabbProperties.Skill config;

	ResultadoHandler(PartidaDoDia partidaDoDia, PartidaService partidas, ResultadoService resultados,
			EnviadorWhatsApp enviador, AabbProperties properties) {
		this.partidaDoDia = partidaDoDia;
		this.partidas = partidas;
		this.resultados = resultados;
		this.enviador = enviador;
		this.config = properties.skill();
	}

	@Override
	public Set<Comando> comandos() {
		return Set.of(Comando.TIMEAZULGANHOU, Comando.TIMEAMARELOGANHOU, Comando.EMPATE);
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
		Resultado resultado = switch (contexto.comando().comando()) {
			case TIMEAZULGANHOU -> Resultado.AZUL;
			case TIMEAMARELOGANHOU -> Resultado.AMARELO;
			default -> Resultado.EMPATE;
		};
		resultados.registrar(partida.id(), resultado, contexto.informadoPor()).ifPresentOrElse(
				registrada -> {
					var sorteio = registrada.sorteioVigente().orElseThrow();
					enviador.enviar(switch (resultado) {
						case AZUL -> Mensagens.vitoria("Azul", config.vitoria(), nomesDosMembros(sorteio.azul()));
						case AMARELO -> Mensagens.vitoria("Amarelo", config.vitoria(), nomesDosMembros(sorteio.amarelo()));
						case EMPATE -> Mensagens.empate(config.empate());
					});
				},
				() -> {
					var atual = partidas.porId(partida.id()).orElseThrow();
					enviador.enviar(atual.temResultado()
							? Mensagens.resultadoJaRegistrado(descricao(atual.resultado()))
							: Mensagens.CANCELADO);
				});
	}

	private static List<String> nomesDosMembros(Time time) {
		return Stream.concat(time.titulares().stream(), time.reservas().stream())
				.filter(Escalado::membro)
				.map(Escalado::nome)
				.toList();
	}

	private static String descricao(Resultado resultado) {
		return switch (resultado) {
			case AZUL -> "Time Azul venceu";
			case AMARELO -> "Time Amarelo venceu";
			case EMPATE -> "empate";
		};
	}
}
