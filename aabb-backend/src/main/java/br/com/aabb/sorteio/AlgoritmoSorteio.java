package br.com.aabb.sorteio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import br.com.aabb.jogador.Posicao;

/**
 * Algoritmo determinístico de sorteio (sem Spring, sem I/O).
 *
 * <p>Membros são equilibrados por skill e posição testando todas as divisões possíveis;
 * convidados são distribuídos aleatoriamente (pela {@code seed}) só para igualar o número de jogadores.
 */
public final class AlgoritmoSorteio {

	public static final int VERSAO = 1;

	private static final double EPSILON = 1e-9;
	private static final int LIMITE_BUSCA_EXAUSTIVA = 20;
	private static final List<Posicao> POSICOES_DE_LINHA = List.of(Posicao.Z, Posicao.L, Posicao.M, Posicao.A);

	private final double toleranciaSkill;

	public AlgoritmoSorteio(double toleranciaSkill) {
		this.toleranciaSkill = toleranciaSkill;
	}

	public record Resultado(Time azul, Time amarelo, String composicao) {
	}

	record Divisao(List<Participante> a, List<Participante> b) {
	}

	/**
	 * @param titulares         titulares na ordem de prioridade (membros e convidados)
	 * @param reservas          reservas na ordem de prioridade
	 * @param composicoesUsadas composições já sorteadas nesta partida (evitadas enquanto houver alternativa)
	 */
	public Resultado sortear(List<Participante> titulares, List<Participante> reservas, long seed,
			Set<String> composicoesUsadas) {
		Random rng = new Random(seed);

		List<Participante> membrosTitulares = titulares.stream().filter(Participante::membro).toList();
		List<Participante> convidadosTitulares = titulares.stream().filter(p -> !p.membro()).toList();

		List<Divisao> candidatas = new ArrayList<>(melhoresDivisoes(membrosTitulares, true));
		Collections.shuffle(candidatas, rng);

		List<Participante> azul = null;
		List<Participante> amarelo = null;
		String composicao = null;
		for (Divisao candidata : candidatas) {
			List<List<Participante>> lados = completarComConvidados(candidata, convidadosTitulares, rng);
			String chave = chave(lados.get(0), lados.get(1));
			if (azul == null || !composicoesUsadas.contains(chave)) {
				azul = lados.get(0);
				amarelo = lados.get(1);
				composicao = chave;
			}
			if (!composicoesUsadas.contains(chave)) {
				break;
			}
		}

		double skillAzul = soma(azul);
		double skillAmarelo = soma(amarelo);
		List<List<Participante>> bancos = dividirReservas(reservas, skillAzul <= skillAmarelo, rng);

		return new Resultado(
				montarTime(azul, bancos.get(0), titulares),
				montarTime(amarelo, bancos.get(1), titulares),
				composicao);
	}

	/** Divisões candidatas: as mais equilibradas em skill (com tolerância) e, entre elas, em posição. */
	List<Divisao> melhoresDivisoes(List<Participante> membros, boolean regraGoleiro) {
		List<Divisao> todas = todasAsDivisoes(membros, regraGoleiro);
		double menorDifSkill = todas.stream().mapToDouble(this::difSkill).min().orElse(0);
		List<Divisao> porSkill = todas.stream()
				.filter(d -> difSkill(d) <= menorDifSkill + toleranciaSkill + EPSILON)
				.toList();
		int menorDifPosicao = porSkill.stream().mapToInt(AlgoritmoSorteio::difPosicao).min().orElse(0);
		return porSkill.stream().filter(d -> difPosicao(d) <= menorDifPosicao + 1).toList();
	}

	/**
	 * Todas as divisões dos membros em dois grupos com diferença de tamanho de no máximo 1.
	 * O primeiro membro fica sempre no grupo A, para não repetir a mesma divisão espelhada.
	 */
	static List<Divisao> todasAsDivisoes(List<Participante> membros, boolean regraGoleiro) {
		int n = membros.size();
		if (n == 0) {
			return List.of(new Divisao(List.of(), List.of()));
		}
		if (n > LIMITE_BUSCA_EXAUSTIVA) {
			return List.of(divisaoGulosa(membros));
		}
		long goleiros = membros.stream().filter(Participante::goleiro).count();
		Set<Integer> tamanhosDeA = n % 2 == 0 ? Set.of(n / 2) : Set.of(n / 2, n / 2 + 1);
		List<Divisao> divisoes = new ArrayList<>();
		for (int mascara = 0; mascara < (1 << (n - 1)); mascara++) {
			if (!tamanhosDeA.contains(Integer.bitCount(mascara) + 1)) {
				continue;
			}
			List<Participante> a = new ArrayList<>(List.of(membros.getFirst()));
			List<Participante> b = new ArrayList<>();
			for (int i = 1; i < n; i++) {
				((mascara & (1 << (i - 1))) != 0 ? a : b).add(membros.get(i));
			}
			if (regraGoleiro && goleiros >= 2 && (semGoleiro(a) || semGoleiro(b))) {
				continue;
			}
			divisoes.add(new Divisao(List.copyOf(a), List.copyOf(b)));
		}
		return divisoes;
	}

	/** Fallback para listas enormes: distribuição em "cobrinha" por skill. */
	private static Divisao divisaoGulosa(List<Participante> membros) {
		List<Participante> ordenados = membros.stream()
				.sorted(Comparator.comparingDouble(Participante::skill).reversed())
				.toList();
		List<Participante> a = new ArrayList<>();
		List<Participante> b = new ArrayList<>();
		for (int i = 0; i < ordenados.size(); i++) {
			(i % 4 == 0 || i % 4 == 3 ? a : b).add(ordenados.get(i));
		}
		return new Divisao(a, b);
	}

	/** Sorteia as cores e completa os times com convidados, sempre no time com menos jogadores. */
	private static List<List<Participante>> completarComConvidados(Divisao divisao, List<Participante> convidados,
			Random rng) {
		boolean aEhAzul = rng.nextBoolean();
		List<Participante> azul = new ArrayList<>(aEhAzul ? divisao.a() : divisao.b());
		List<Participante> amarelo = new ArrayList<>(aEhAzul ? divisao.b() : divisao.a());
		List<Participante> embaralhados = new ArrayList<>(convidados);
		Collections.shuffle(embaralhados, rng);
		for (Participante convidado : embaralhados) {
			boolean paraAzul = azul.size() != amarelo.size() ? azul.size() < amarelo.size() : rng.nextBoolean();
			(paraAzul ? azul : amarelo).add(convidado);
		}
		return List.of(azul, amarelo);
	}

	/**
	 * Reservas: membros equilibrados por skill e posição; o grupo maior vai para o time com menor skill
	 * nos titulares. Convidados completam o banco menor (empate: time com menor skill).
	 */
	private List<List<Participante>> dividirReservas(List<Participante> reservas, boolean azulMaisFraco,
			Random rng) {
		List<Participante> membros = reservas.stream().filter(Participante::membro).toList();
		List<Divisao> candidatas = melhoresDivisoes(membros, false);
		Divisao escolhida = candidatas.get(rng.nextInt(candidatas.size()));
		List<Participante> maior = escolhida.a().size() >= escolhida.b().size() ? escolhida.a() : escolhida.b();
		List<Participante> menor = maior == escolhida.a() ? escolhida.b() : escolhida.a();
		boolean maiorEhSorteado = maior.size() == menor.size() && rng.nextBoolean();
		boolean maiorParaAzul = maiorEhSorteado ? !azulMaisFraco : azulMaisFraco;

		List<Participante> bancoAzul = new ArrayList<>(maiorParaAzul ? maior : menor);
		List<Participante> bancoAmarelo = new ArrayList<>(maiorParaAzul ? menor : maior);
		reservas.stream().filter(p -> !p.membro()).forEach(convidado -> {
			boolean paraAzul = bancoAzul.size() != bancoAmarelo.size()
					? bancoAzul.size() < bancoAmarelo.size()
					: azulMaisFraco;
			(paraAzul ? bancoAzul : bancoAmarelo).add(convidado);
		});
		return List.of(bancoAzul, bancoAmarelo);
	}

	/** Monta o time mantendo a ordem de prioridade original e designando o primeiro goleiro. */
	private static Time montarTime(List<Participante> titulares, List<Participante> reservas,
			List<Participante> ordemOriginal) {
		List<Participante> ordenados = titulares.stream()
				.sorted(Comparator.comparingInt(ordemOriginal::indexOf))
				.toList();
		Participante goleiro = ordenados.stream().filter(Participante::goleiro).findFirst().orElse(null);
		List<Escalado> escalados = ordenados.stream()
				.map(p -> new Escalado(p.tipo(), p.jogadorId(), p.nome(), p == goleiro))
				.toList();
		List<Escalado> banco = reservas.stream()
				.map(p -> new Escalado(p.tipo(), p.jogadorId(), p.nome(), false))
				.toList();
		return new Time(escalados, banco, Math.round(soma(titulares) * 100) / 100.0);
	}

	private double difSkill(Divisao d) {
		return Math.abs(soma(d.a()) - soma(d.b()));
	}

	private static int difPosicao(Divisao d) {
		var a = contarPosicoes(d.a());
		var b = contarPosicoes(d.b());
		return POSICOES_DE_LINHA.stream().mapToInt(p -> Math.abs(a.get(p) - b.get(p))).sum();
	}

	private static EnumMap<Posicao, Integer> contarPosicoes(List<Participante> grupo) {
		EnumMap<Posicao, Integer> contagem = new EnumMap<>(Posicao.class);
		POSICOES_DE_LINHA.forEach(p -> contagem.put(p, 0));
		grupo.stream()
				.map(Participante::principal)
				.filter(POSICOES_DE_LINHA::contains)
				.forEach(p -> contagem.merge(p, 1, Integer::sum));
		return contagem;
	}

	private static boolean semGoleiro(List<Participante> grupo) {
		return grupo.stream().noneMatch(Participante::goleiro);
	}

	private static double soma(Collection<Participante> grupo) {
		return grupo.stream().filter(Participante::membro).mapToDouble(Participante::skill).sum();
	}

	/** Chave da composição, independente da cor: "a,b,c|d,e,f" com os lados em ordem alfabética. */
	static String chave(List<Participante> lado1, List<Participante> lado2) {
		String x = lado1.stream().map(Participante::chave).sorted().collect(Collectors.joining(","));
		String y = lado2.stream().map(Participante::chave).sorted().collect(Collectors.joining(","));
		return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
	}
}
