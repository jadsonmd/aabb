package br.com.aabb.comando;

import java.util.Set;

/**
 * Executa um comando do WhatsApp. Cada hashtag tem exatamente um handler.
 */
public interface ComandoHandler {

	Set<Comando> comandos();

	void executar(Contexto contexto);
}
