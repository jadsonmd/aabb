package br.com.aabb.resultado;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aabb.config.AabbProperties;
import br.com.aabb.jogador.SkillService;
import br.com.aabb.partida.Partida;
import br.com.aabb.partida.PartidaService;
import br.com.aabb.partida.Resultado;

/**
 * Registra o resultado da partida (uma única vez) e aplica o bônus de skill na mesma transação.
 */
@Service
class ResultadoService {

	private final PartidaService partidas;
	private final SkillService skill;
	private final AabbProperties.Skill config;

	ResultadoService(PartidaService partidas, SkillService skill, AabbProperties properties) {
		this.partidas = partidas;
		this.skill = skill;
		this.config = properties.skill();
	}

	/** @return a partida com o resultado, ou vazio se o resultado já havia sido registrado */
	@Transactional
	public Optional<Partida> registrar(String partidaId, Resultado resultado, String informadoPor) {
		var partida = partidas.registrarResultado(partidaId, resultado, informadoPor);
		partida.ifPresent(p -> {
			var sorteio = p.sorteioVigente().orElseThrow();
			switch (resultado) {
				case AZUL -> skill.ajustar(sorteio.azul().membros(), config.vitoria(), "VITORIA", p.id());
				case AMARELO -> skill.ajustar(sorteio.amarelo().membros(), config.vitoria(), "VITORIA", p.id());
				case EMPATE -> skill.ajustar(sorteio.membros(), config.empate(), "EMPATE", p.id());
			}
		});
		return partida;
	}
}
