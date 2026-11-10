package br.com.aabb.fluxo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.aabb.comando.Mensagens;
import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.Posicao;
import br.com.aabb.partida.PartidaService;

@AutoConfigureMockMvc
class PainelApiTest extends TesteDeIntegracao {

	@Autowired
	MockMvc mvc;
	@Autowired
	PartidaService partidas;

	@Test
	void apiDoPainelExigeTokenEWebhookNao() throws Exception {
		mvc.perform(get("/api/painel/ranking")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk());
	}

	@Test
	void cadastroRankingEFinalizacaoDaPartida() throws Exception {
		mvc.perform(post("/api/painel/jogadores").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Jadson Mezzari","apelido":"Jadson","whatsapp":"5511900000001","posicoes":["M","A"],
				 "skillBase":7.5,"admin":true}
				"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.skill").value(7.5));
		mvc.perform(post("/api/painel/jogadores").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Outro","apelido":"Outro","whatsapp":"5511900000001","posicoes":["M"],"skillBase":5}
				""")).andExpect(status().isConflict());
		mvc.perform(post("/api/painel/jogadores").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"","apelido":"X","whatsapp":"abc","posicoes":[],"skillBase":11}
				""")).andExpect(status().isBadRequest());

		Jogador adm = jogadores.listar().getFirst();
		texto(adm, "#abrirlista");
		texto(adm, "#vou");
		for (int i = 0; i < 9; i++) {
			texto(membro("J" + i, "55119100000" + i, 5, Posicao.M), "#vou");
		}
		texto(adm, "#sortear");
		String partidaId = partidas.doDia(SEGUNDA_10H.toLocalDate()).orElseThrow().id();

		// Quem só está no grupo e não teve atividade no ano não aparece no ranking.
		membro("SoAssiste", "5511988880000", 5, Posicao.L);
		mvc.perform(get("/api/painel/ranking").with(jwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(10))
				.andExpect(jsonPath("$[0].apelido").value("Jadson"))
				.andExpect(jsonPath("$[0].presencas").value(1));

		mvc.perform(post("/api/painel/partidas/{id}/finalizar", partidaId).with(jwt())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"placarAzul":3,"placarAmarelo":2,
						 "eventos":[{"tipo":"ASSISTENCIA","jogadorId":"%s","nome":"Jadson"}]}
						""".formatted(adm.id())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FINALIZADA"));

		mvc.perform(get("/api/painel/partidas/{id}", partidaId).with(jwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.partida.placarAzul").value(3))
				.andExpect(jsonPath("$.presentes.length()").value(10));

		texto(adm, "#ressortear");
		assertThat(ultimaMensagem()).isEqualTo(Mensagens.RESULTADO_TRAVA_TIMES);
	}
}
