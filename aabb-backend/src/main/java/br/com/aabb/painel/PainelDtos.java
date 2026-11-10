package br.com.aabb.painel;

import java.util.List;

import br.com.aabb.lance.Lance;
import br.com.aabb.partida.EventoDaPartida;
import br.com.aabb.partida.Partida;
import br.com.aabb.partida.Resultado;
import br.com.aabb.partida.StatusPartida;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Respostas e requisições da API do painel. */
final class PainelDtos {

	private PainelDtos() {
	}

	/**
	 * Linha do ranking (valores do ano).
	 *
	 * @param canetas canetas e chapéus aprovados pelo VAR, separados
	 */
	record LinhaDoRanking(String jogadorId, String nome, String apelido, double skill, int gols, int canetas,
			int chapeus, int titulos, int presencas) {
	}

	record ResumoDaPartida(String id, String data, StatusPartida status, int confirmados, Resultado resultado,
			Integer placarAzul, Integer placarAmarelo) {

		static ResumoDaPartida de(Partida p) {
			return new ResumoDaPartida(p.id(), p.data(), p.status(), p.confirmados().size(), p.resultado(),
					p.placarAzul(), p.placarAmarelo());
		}
	}

	/** Partida completa com os gols aprovados pelo VAR e os membros que receberam presença. */
	record DetalheDaPartida(Partida partida, List<Lance> gols, List<String> presentes) {
	}

	record PresencasDoJogador(String jogadorId, String apelido, int noAno, int total, List<String> datas) {
	}

	record FinalizarPartida(
			@NotNull @Min(0) @Max(99) Integer placarAzul,
			@NotNull @Min(0) @Max(99) Integer placarAmarelo,
			@NotNull List<@Valid Evento> eventos) {

		/** @param jogadorId {@code null} para convidados (informar {@code nome}) */
		record Evento(@NotNull EventoDaPartida.Tipo tipo, String jogadorId, @NotBlank String nome) {
		}
	}
}
