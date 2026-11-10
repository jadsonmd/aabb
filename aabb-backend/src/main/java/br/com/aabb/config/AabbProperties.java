package br.com.aabb.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import br.com.aabb.jogador.Posicao;

/**
 * Configurações do sistema AABB (prefixo {@code aabb}).
 */
@ConfigurationProperties("aabb")
public record AabbProperties(
		String grupoJid,
		Evolution evolution,
		Agenda agenda,
		Sorteio sorteio,
		Skill skill,
		Cadastro cadastro) {

	public boolean grupoConfigurado() {
		return grupoJid != null && !grupoJid.isBlank();
	}

	public record Evolution(String url, String apiKey, String instancia, int delayMs) {
	}

	/**
	 * Dias e horário dos jogos. O cron de abertura deve abrir a lista nos mesmos dias de {@code dias}.
	 */
	public record Agenda(List<DayOfWeek> dias, LocalTime horario, ZoneId zona, String aberturaCron) {
	}

	/**
	 * Valores do pré-cadastro automático de quem manda mensagem no grupo sem estar cadastrado.
	 */
	public record Cadastro(double skillInicial, Posicao posicaoInicial) {
	}

	public record Sorteio(int minimoJogadores, int titulares, double toleranciaSkill) {
	}

	public record Skill(
			double minimo,
			double maximo,
			int votosNecessarios,
			double jogadaEfeito,
			double gol,
			int maximoPorLance,
			double vitoria,
			double empate) {
	}
}
