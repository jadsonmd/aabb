package br.com.aabb.whatsapp;

/**
 * Processa uma mensagem do grupo oficial (comando ou reação).
 */
public interface ProcessadorDeMensagem {

	void processar(MensagemRecebida mensagem);
}
