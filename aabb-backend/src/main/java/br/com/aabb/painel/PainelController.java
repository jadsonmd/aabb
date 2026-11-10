package br.com.aabb.painel;

import java.time.Clock;
import java.time.Year;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.aabb.painel.PainelDtos.DetalheDaPartida;
import br.com.aabb.painel.PainelDtos.FinalizarPartida;
import br.com.aabb.painel.PainelDtos.LinhaDoRanking;
import br.com.aabb.painel.PainelDtos.PresencasDoJogador;
import br.com.aabb.painel.PainelDtos.ResumoDaPartida;
import br.com.aabb.partida.Partida;
import jakarta.validation.Valid;

/**
 * API de leitura e do pós-jogo usada pelo painel administrativo.
 */
@RestController
@RequestMapping("/api/painel")
class PainelController {

	private final PainelService service;
	private final Clock clock;

	PainelController(PainelService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@GetMapping("/ranking")
	List<LinhaDoRanking> ranking(@RequestParam(required = false) Integer ano) {
		return service.ranking(ano == null ? Year.now(clock).getValue() : ano);
	}

	@GetMapping("/presencas")
	List<PresencasDoJogador> presencas(@RequestParam(required = false) Integer ano) {
		return service.presencas(ano == null ? Year.now(clock).getValue() : ano);
	}

	@GetMapping("/partidas")
	List<ResumoDaPartida> partidas() {
		return service.partidasRecentes();
	}

	@GetMapping("/partidas/{id}")
	DetalheDaPartida partida(@PathVariable String id) {
		return service.partida(id);
	}

	@PostMapping("/partidas/{id}/finalizar")
	Partida finalizar(@PathVariable String id, @Valid @RequestBody FinalizarPartida request) {
		return service.finalizar(id, request);
	}
}
