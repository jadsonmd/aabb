package br.com.aabb.sorteio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import br.com.aabb.jogador.Posicao;
import br.com.aabb.partida.TipoConfirmado;

class AlgoritmoSorteioTest {

	private static final double TOLERANCIA = 0.5;
	private final AlgoritmoSorteio algoritmo = new AlgoritmoSorteio(TOLERANCIA);
	private final Random aleatorio = new Random();

	@RepeatedTest(300)
	void respeitaTodasAsRegrasParaListasAleatorias() {
		int membros = aleatorio.nextInt(0, 19);
		int convidados = aleatorio.nextInt(0, 5);
		if (membros + convidados < 10) {
			convidados = 10 - membros;
		}
		List<Participante> fila = fila(membros, convidados);
		int quantosTitulares = Math.min(14, fila.size());
		List<Participante> titulares = fila.subList(0, quantosTitulares);
		List<Participante> reservas = fila.subList(quantosTitulares, fila.size());

		var resultado = algoritmo.sortear(titulares, reservas, aleatorio.nextLong(), Set.of());
		Time azul = resultado.azul();
		Time amarelo = resultado.amarelo();

		// Times com o mesmo número de titulares (ou 1 de diferença) e bancos equilibrados.
		assertThat(Math.abs(azul.titulares().size() - amarelo.titulares().size())).isLessThanOrEqualTo(1);
		assertThat(Math.abs(azul.reservas().size() - amarelo.reservas().size())).isLessThanOrEqualTo(1);

		// Todo mundo aparece uma única vez, e titular não vira reserva (nem o contrário).
		assertThat(nomes(azul.titulares(), amarelo.titulares()))
				.containsExactlyInAnyOrderElementsOf(titulares.stream().map(Participante::nome).toList());
		assertThat(nomes(azul.reservas(), amarelo.reservas()))
				.containsExactlyInAnyOrderElementsOf(reservas.stream().map(Participante::nome).toList());

		// No máximo 1 goleiro por time; com 2+ goleiros entre os membros titulares, cada time tem 1.
		long goleiros = titulares.stream().filter(p -> p.membro() && p.goleiro()).count();
		assertThat(azul.titulares().stream().filter(Escalado::goleiro).count()).isLessThanOrEqualTo(1);
		assertThat(amarelo.titulares().stream().filter(Escalado::goleiro).count()).isLessThanOrEqualTo(1);
		if (goleiros >= 2) {
			assertThat(azul.goleiro()).isPresent();
			assertThat(amarelo.goleiro()).isPresent();
		}

		// Diferença de skill dentro da tolerância em relação à melhor divisão possível.
		List<Participante> membrosTitulares = titulares.stream().filter(Participante::membro).toList();
		double melhor = AlgoritmoSorteio.todasAsDivisoes(membrosTitulares, true).stream()
				.mapToDouble(d -> Math.abs(soma(d.a()) - soma(d.b())))
				.min().orElse(0);
		double obtida = Math.abs(azul.somaSkillTitulares() - amarelo.somaSkillTitulares());
		assertThat(obtida).isLessThanOrEqualTo(melhor + TOLERANCIA + 0.011);
	}

	@Test
	void mesmaSementeGeraMesmoResultado() {
		List<Participante> fila = fila(14, 2);
		var titulares = fila.subList(0, 14);
		var reservas = fila.subList(14, 16);
		var primeiro = algoritmo.sortear(titulares, reservas, 42L, Set.of());
		var segundo = algoritmo.sortear(titulares, reservas, 42L, Set.of());
		assertThat(segundo).isEqualTo(primeiro);
	}

	@Test
	void contaQuantasDivisoesExistem() {
		assertThat(AlgoritmoSorteio.todasAsDivisoes(fila(14, 0), false)).hasSize(1716);
		assertThat(AlgoritmoSorteio.todasAsDivisoes(fila(13, 0), false)).hasSize(1716);
		assertThat(AlgoritmoSorteio.todasAsDivisoes(fila(10, 0), false)).hasSize(126);
	}

	@Test
	void ressorteioEvitaComposicoesJaUsadas() {
		List<Participante> titulares = fila(14, 0);
		Set<String> usadas = new HashSet<>();
		for (int i = 0; i < 5; i++) {
			var resultado = algoritmo.sortear(titulares, List.of(), aleatorio.nextLong(), usadas);
			assertThat(usadas).doesNotContain(resultado.composicao());
			usadas.add(resultado.composicao());
		}
	}

	@Test
	void convidadosNaoEntramNaSomaDeSkill() {
		List<Participante> titulares = new ArrayList<>(fila(8, 0));
		titulares.add(convidado("Marcelo"));
		titulares.add(convidado("Felipe"));
		var resultado = algoritmo.sortear(titulares, List.of(), 7L, Set.of());
		double somaMembros = titulares.stream().filter(Participante::membro).mapToDouble(Participante::skill).sum();
		assertThat(resultado.azul().somaSkillTitulares() + resultado.amarelo().somaSkillTitulares())
				.isCloseTo(somaMembros, offset(0.02));
		assertThat(resultado.azul().titulares()).hasSize(5);
		assertThat(resultado.amarelo().titulares()).hasSize(5);
	}

	@Test
	void semGoleirosOSorteioSegueSemApontarGoleiro() {
		List<Participante> titulares = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			titulares.add(membro("J" + i, 5 + i * 0.1, Posicao.M));
		}
		var resultado = algoritmo.sortear(titulares, List.of(), 1L, Set.of());
		assertThat(resultado.azul().goleiro()).isEmpty();
		assertThat(resultado.amarelo().goleiro()).isEmpty();
	}

	private List<Participante> fila(int membros, int convidados) {
		List<Participante> fila = new ArrayList<>();
		Posicao[] posicoes = Posicao.values();
		for (int i = 0; i < membros; i++) {
			double skill = Math.round(aleatorio.nextDouble(3, 10) * 10) / 10.0;
			fila.add(membro("M" + i, skill, posicoes[aleatorio.nextInt(posicoes.length)]));
		}
		for (int i = 0; i < convidados; i++) {
			fila.add(convidado("C" + i));
		}
		return fila;
	}

	private static Participante membro(String nome, double skill, Posicao posicao) {
		return new Participante(TipoConfirmado.MEMBRO, nome, nome, nome, skill, posicao);
	}

	private static Participante convidado(String nome) {
		return new Participante(TipoConfirmado.CONVIDADO, "c:" + nome, null, nome, 0, null);
	}

	private static double soma(List<Participante> grupo) {
		return grupo.stream().mapToDouble(Participante::skill).sum();
	}

	private static List<String> nomes(List<Escalado> a, List<Escalado> b) {
		return Stream.concat(a.stream(), b.stream()).map(Escalado::nome).toList();
	}
}
