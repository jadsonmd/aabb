package br.com.aabb.painel;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.JogadorRequest;
import br.com.aabb.jogador.JogadorService;
import jakarta.validation.Valid;

/**
 * Cadastro de membros (painel do admin).
 */
@RestController
@RequestMapping("/api/painel/jogadores")
@Validated
class JogadorController {

	private final JogadorService service;

	JogadorController(JogadorService service) {
		this.service = service;
	}

	@GetMapping
	List<Jogador> listar() {
		return service.listar();
	}

	@GetMapping("/{id}")
	Jogador buscar(@PathVariable String id) {
		return service.buscar(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Jogador criar(@Valid @RequestBody JogadorRequest request) {
		return service.criar(request);
	}

	/** Importação em lote (ex: carga inicial dos membros). */
	@PostMapping("/importar")
	@ResponseStatus(HttpStatus.CREATED)
	List<Jogador> importar(@RequestBody List<@Valid JogadorRequest> requests) {
		return requests.stream().map(service::criar).toList();
	}

	@PutMapping("/{id}")
	Jogador atualizar(@PathVariable String id, @Valid @RequestBody JogadorRequest request) {
		return service.atualizar(id, request);
	}

	@PostMapping("/{id}/ativar")
	Jogador ativar(@PathVariable String id) {
		return service.definirAtivo(id, true);
	}

	@PostMapping("/{id}/desativar")
	Jogador desativar(@PathVariable String id) {
		return service.definirAtivo(id, false);
	}

	@DeleteMapping("/{id}/lids/{lid}")
	Jogador desvincularLid(@PathVariable String id, @PathVariable String lid) {
		return service.desvincularLid(id, lid);
	}
}
