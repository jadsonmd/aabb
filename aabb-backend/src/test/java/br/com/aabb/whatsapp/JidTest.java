package br.com.aabb.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JidTest {

	@Test
	void removeSufixoDeDispositivoEClassificaPeloServidor() {
		assertThat(Jid.of("5511900000000:12@s.whatsapp.net")).isEqualTo(new Jid("5511900000000", Jid.Tipo.NUMERO));
		assertThat(Jid.of("987654321098765@lid")).isEqualTo(new Jid("987654321098765", Jid.Tipo.LID));
		assertThat(Jid.of("120363000000000000@g.us").tipo()).isEqualTo(Jid.Tipo.GRUPO);
		assertThat(Jid.of(" ")).isNull();
	}

	@Test
	void montaJidCompleto() {
		assertThat(Jid.numero("5511900000000").completo()).isEqualTo("5511900000000@s.whatsapp.net");
		assertThat(Jid.of("123@lid").completo()).isEqualTo("123@lid");
	}
}
