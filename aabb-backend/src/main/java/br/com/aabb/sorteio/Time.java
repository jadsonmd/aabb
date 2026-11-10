package br.com.aabb.sorteio;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @param somaSkillTitulares soma da skill dos membros titulares (convidados não entram)
 */
public record Time(List<Escalado> titulares, List<Escalado> reservas, double somaSkillTitulares) {

	public Time {
		titulares = List.copyOf(titulares);
		reservas = List.copyOf(reservas);
	}

	public Optional<Escalado> goleiro() {
		return titulares.stream().filter(Escalado::goleiro).findFirst();
	}

	public long convidadosTitulares() {
		return titulares.stream().filter(e -> !e.membro()).count();
	}

	/** Ids dos membros do time (titulares e reservas). */
	public List<String> membros() {
		return Stream.concat(titulares.stream(), reservas.stream())
				.filter(Escalado::membro)
				.map(Escalado::jogadorId)
				.toList();
	}
}
