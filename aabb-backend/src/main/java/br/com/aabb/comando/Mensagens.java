package br.com.aabb.comando;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import br.com.aabb.partida.Confirmado;
import br.com.aabb.sorteio.Escalado;
import br.com.aabb.sorteio.Sorteio;
import br.com.aabb.sorteio.Time;

/**
 * Textos enviados pelo bot ao grupo.
 */
public final class Mensagens {

	public static final String NAO_RECONHECIDO = "Não reconheci seu número. Peça a um admin para conferir seu cadastro.";
	public static final String SO_ADMIN = "Comando exclusivo de administradores.";
	public static final String LISTA_NAO_ABERTA = "A lista de hoje ainda não foi aberta.";
	public static final String LISTA_JA_ABERTA = "A lista de hoje já está aberta.";
	public static final String JA_SORTEADO = "Os times já foram sorteados!";
	public static final String CANCELADO = "O jogo de hoje foi cancelado.";
	public static final String JOGO_CANCELADO = "❌ Jogo de hoje cancelado.";
	public static final String SEM_SORTEIO = "Ainda não houve sorteio.";
	public static final String SEM_SORTEIO_USE_SORTEAR = "Ainda não houve sorteio. Use #sortear.";
	public static final String RESULTADO_TRAVA_CANCELAMENTO = "O resultado já foi registrado; o jogo não pode ser cancelado.";
	public static final String JA_SORTEADO_USE_RESSORTEAR = "Os times já foram sorteados! Para trocar, use #ressortear.";
	public static final String HORARIO_INVALIDO = "Horário inválido. Ex: #abrirlista 9h ou #abrirlista 20h30";
	public static final String USO_JOGADA = "Formato: #canetou @vitima 2x (lance seu) ou @autor #canetou @vitima 2x";
	public static final String RESULTADO_TRAVA_TIMES = "O resultado já foi registrado, os times não podem mais mudar.";
	public static final String NINGUEM_IDENTIFICADO = "Não reconheci nenhum jogador nesse lance.";
	public static final String QUANTIDADE_INVALIDA = "Quantidade inválida. Use de 1x a 10x, ex: #marcou 2x.";

	private Mensagens() {
	}

	public static String horarioAlterado(String descricaoJogo) {
		return "🕗 Horário do jogo de hoje alterado: " + descricaoJogo + ".";
	}

	public static String listaAberta(String descricaoJogo) {
		return "⚽ Lista aberta para o jogo de hoje (" + descricaoJogo + ")! Mande #vou para confirmar.";
	}

	/** Mensagem da Lista: todos os confirmados em ordem de confirmação. */
	public static String lista(String cabecalho, String descricaoJogo, List<Confirmado> confirmados) {
		String nomes = IntStream.range(0, confirmados.size())
				.mapToObj(i -> (i + 1) + ". " + nomeNaLista(confirmados.get(i)))
				.collect(Collectors.joining("\n"));
		String corpo = "⚽ Jogo de hoje — " + descricaoJogo + "\nConfirmados (" + confirmados.size() + "):"
				+ (nomes.isEmpty() ? "" : "\n" + nomes);
		return cabecalho == null ? corpo : cabecalho + "\n\n" + corpo;
	}

	public static String nomeNaLista(Confirmado c) {
		return c.membro() ? c.nome() : c.nome() + " (convidado de " + c.convidadoPorNome() + ")";
	}

	public static String confirmado(String nome) {
		return "✅ " + nome + " confirmado!";
	}

	public static String jaNaLista(String nome) {
		return nome + " já está na lista.";
	}

	public static String foraDaLista(String nome) {
		return nome + " não está na lista.";
	}

	public static String convidadoEntrou(Confirmado convidado) {
		return "✅ " + nomeNaLista(convidado) + " entrou na lista!";
	}

	public static String convidadoRemovido(String nome, int confirmados) {
		return nome + " removido da lista. Confirmados: " + confirmados + ".";
	}

	public static String saiuDaLista(String nome, int confirmados) {
		return nome + " saiu da lista. Confirmados: " + confirmados + ".";
	}

	public static String usoConvidado() {
		return "Informe o nome do convidado. Ex: #convidado Marcelo";
	}

	public static String usoDesconvidar() {
		return "Informe o nome do convidado. Ex: #desconvidar Marcelo";
	}

	public static String convidadoNaoEncontrado(String nome) {
		return nome + " não está na lista de convidados.";
	}

	public static String minimoParaSortear(int confirmados, int minimo) {
		return "Mínimo de " + minimo + " jogadores para sortear. Temos " + confirmados + "/" + minimo + ".";
	}

	public static String times(String descricaoJogo, Sorteio sorteio) {
		String titulo = sorteio.versao() > 1
				? "🔄 Novo sorteio (#" + sorteio.versao() + ") — " + descricaoJogo
				: "⚽ Times de hoje — " + descricaoJogo;
		return titulo + "\n\n" + time("🔵 Time Azul", sorteio.azul()) + "\n\n" + time("🟡 Time Amarelo", sorteio.amarelo());
	}

	private static String time(String nome, Time time) {
		long convidados = time.convidadosTitulares();
		String skill = "Skill " + decimal(time.somaSkillTitulares())
				+ (convidados > 0 ? " (+" + convidados + (convidados == 1 ? " convidado)" : " convidados)") : "");
		var linhas = new StringBuilder(nome).append(" — ").append(skill);
		time.titulares().forEach(e -> linhas.append("\n").append(e.goleiro() ? "🧤 " : "• ").append(nomeEscalado(e)));
		if (!time.reservas().isEmpty()) {
			linhas.append("\nReservas: ")
					.append(time.reservas().stream().map(Mensagens::nomeEscalado).collect(Collectors.joining(", ")));
		}
		return linhas.toString();
	}

	private static String nomeEscalado(Escalado e) {
		return e.membro() ? e.nome() : e.nome() + " (convidado)";
	}

	/** Linhas "🥇 Nome — valor", numeradas a partir do 4º lugar. */
	public static String classificacao(String titulo, List<String> nomes, List<String> valores, String vazio) {
		if (nomes.isEmpty()) {
			return titulo + "\n" + vazio;
		}
		var texto = new StringBuilder(titulo);
		for (int i = 0; i < nomes.size(); i++) {
			String posicao = i < 3 ? new String[] { "🥇", "🥈", "🥉" }[i] : (i + 1) + ".";
			texto.append("\n").append(posicao).append(" ").append(nomes.get(i)).append(" — ").append(valores.get(i));
		}
		return texto.toString();
	}

	public static String resultadoJaRegistrado(String resultado) {
		return "O resultado de hoje já foi registrado: " + resultado + ".";
	}

	public static String vitoria(String time, double bonus, List<String> nomes) {
		return "🏆 Time " + time + " venceu! +" + decimal(bonus) + " de skill para: " + String.join(", ", nomes);
	}

	public static String empate(double bonus) {
		return "🤝 Empate! +" + decimal(bonus) + " de skill para todos que jogaram.";
	}

	/** @param jogada ex: "Caneta" ou "3 canetas" */
	public static String lanceNoVar(String jogada, String autor, String naoIdentificado, int votos) {
		return "🔍 " + jogada + " de " + autor + " na cabine do VAR. Precisa de " + votos + " 👍 para valer."
				+ (naoIdentificado == null ? "" : " (" + naoIdentificado + " não identificado, não terá alteração de skill.)");
	}

	public static String lanceAprovado(String jogada, String autor, String vitima) {
		return "✅ VAR confirmou: " + jogada + " de " + autor + (vitima == null ? "" : " em " + vitima) + "!";
	}

	public static String golsNoVar(int gols, String nome, int votos) {
		return "🔍 " + gols + (gols == 1 ? " gol" : " gols") + " de " + nome + " na cabine do VAR. Precisa de "
				+ votos + " 👍 para valer.";
	}

	public static String golsAprovados(int gols, String nome, double bonus) {
		return "⚽ VAR confirmou: " + gols + (gols == 1 ? " gol" : " gols") + " de " + nome + "! +" + decimal(bonus)
				+ " de skill.";
	}

	public static String golsJaRegistrados(String nome, int gols) {
		return "Gols de " + nome + " já registrados hoje: " + gols + ".";
	}

	public static String golsNaoComputados(String nome) {
		return "Gols de " + nome + " não computados: só membros que estão no jogo de hoje.";
	}

	public static String skillAtual(String nome, double skill) {
		return nome + " " + decimal(skill);
	}

	public static String decimal(double valor) {
		return String.format(Locale.ROOT, "%.1f", valor);
	}
}
