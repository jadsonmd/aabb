package br.com.aabb.fluxo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.Posicao;

class ConsultasTest extends TesteDeIntegracao {

	@Test
	void presencaERankingFuncionamEmQualquerDiaParaQualquerMembro() {
		Jogador adm = admin("Adm", "5511900000001", 6.5, Posicao.M);
		List<Jogador> membros = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			membros.add(membro("J" + i, "55119100000" + i, 5 + i * 0.1, Posicao.M));
		}
		Jogador soAssiste = membro("SoAssiste", "5511988880000", 9.9, Posicao.L);

		// Segunda: Adm + J0..J8. Quinta: Adm + J1..J9.
		jogo(adm, membros.subList(0, 9));
		relogio.definir(SEGUNDA_10H.plusDays(3));
		jogo(adm, membros.subList(1, 10));

		// Terça (sem jogo): as consultas funcionam mesmo assim.
		relogio.definir(SEGUNDA_10H.plusDays(8));
		texto(membros.get(9), "#presença");
		String presencas = ultimaMensagem();
		assertThat(presencas).startsWith("📋 Presenças em 2026\n🥇 Adm — 2\n🥈 J1 — 2\n🥉 J2 — 2\n4. J3 — 2");
		assertThat(presencas).endsWith("9. J8 — 2\n10. J0 — 1\n11. J9 — 1");
		assertThat(presencas).doesNotContain("SoAssiste");

		texto(membros.get(0), "#ranking");
		String ranking = ultimaMensagem();
		assertThat(ranking).startsWith("🏆 Ranking de habilidade\n🥇 Adm — 6.5\n🥈 J9 — 5.9\n🥉 J8 — 5.8\n4. J7 — 5.7");
		assertThat(ranking).endsWith("11. J0 — 5.0");
		assertThat(ranking).doesNotContain("SoAssiste");
	}

	@Test
	void titulosNoAnoDeQuemTemMaisParaQuemTemMenos() {
		Jogador adm = admin("Adm", "5511900000001", 6.5, Posicao.M);
		List<Jogador> membros = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			membros.add(membro("J" + i, "55119100000" + i, 5 + i * 0.1, Posicao.M));
		}
		// Duas partidas: o Azul vence a primeira, a segunda empata (empate não dá título).
		jogo(adm, membros);
		texto(adm, "#timeazulganhou");
		relogio.definir(SEGUNDA_10H.plusDays(3));
		jogo(adm, membros);
		texto(adm, "#empate");

		relogio.definir(SEGUNDA_10H.plusDays(8));
		texto(membros.getFirst(), "#títulos");
		String titulos = ultimaMensagem();
		assertThat(titulos).startsWith("🏆 Títulos em 2026\n🥇 ");
		// 10 jogadores, 5 no Azul campeão: só eles aparecem, com 1 título cada.
		assertThat(titulos.lines().skip(1)).hasSize(5).allMatch(l -> l.endsWith(" — 1"));
	}

	@Test
	void semJogosAsConsultasAvisam() {
		Jogador adm = admin("Adm", "5511900000001", 6.5, Posicao.M);
		texto(adm, "#ranking");
		assertThat(ultimaMensagem()).isEqualTo("🏆 Ranking de habilidade\nNinguém jogou ainda.");
		texto(adm, "#presenca");
		assertThat(ultimaMensagem()).isEqualTo("📋 Presenças em 2026\nNenhuma presença registrada este ano.");
		texto(adm, "#titulos");
		assertThat(ultimaMensagem()).isEqualTo("🏆 Títulos em 2026\nNenhum título registrado este ano.");
	}

	private void jogo(Jogador adm, List<Jogador> jogadores) {
		texto(adm, "#abrirlista");
		texto(adm, "#vou");
		jogadores.forEach(j -> texto(j, "#vou"));
		texto(adm, "#sortear");
		assertThat(ultimaMensagem()).startsWith("⚽ Times de hoje");
	}
}
