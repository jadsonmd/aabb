package br.com.aabb.fluxo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.aabb.jogador.HistoricoSkill;
import br.com.aabb.jogador.Jogador;
import br.com.aabb.jogador.Posicao;

@AutoConfigureMockMvc
class EdicaoPeloAdminTest extends TesteDeIntegracao {

	@Autowired
	MockMvc mvc;

	@Test
	void adminEditaSkillEPresencasNoPainel() throws Exception {
		Jogador adm = admin("Adm", "5511900000001", 6, Posicao.M);
		Jogador joao = membro("João", "5511900000002", 5, Posicao.A);
		// João tem 1 presença computada pelo sorteio.
		texto(adm, "#abrirlista");
		texto(adm, "#vou");
		texto(joao, "#vou");
		for (int i = 0; i < 8; i++) {
			texto(membro("J" + i, "55119100000" + i, 5, Posicao.M), "#vou");
		}
		texto(adm, "#sortear");

		editar(joao, 8.3, 5).andExpect(status().isOk())
				.andExpect(jsonPath("$.skill").value(8.3))
				.andExpect(jsonPath("$.ajustesDePresenca.2026").value(4));

		HistoricoSkill ajuste = mongo.findOne(Query.query(Criteria.where("jogadorId").is(joao.id())
				.and("motivo").is("AJUSTE_MANUAL")), HistoricoSkill.class);
		assertThat(ajuste.delta()).isEqualTo(3.3);

		mvc.perform(get("/api/painel/presencas").with(jwt()))
				.andExpect(jsonPath("$[0].apelido").value("João"))
				.andExpect(jsonPath("$[0].noAno").value(5));

		texto(adm, "#presenca");
		assertThat(ultimaMensagem()).startsWith("📋 Presenças em 2026\n🥇 João — 5\n🥈 Adm — 1");
		texto(adm, "#ranking");
		assertThat(ultimaMensagem()).startsWith("🏆 Ranking de habilidade\n🥇 João — 8.3\n🥈 Adm — 6.0");

		// Voltar ao valor computado remove o ajuste; sem os campos, nada muda.
		editar(joao, null, 1).andExpect(jsonPath("$.skill").value(8.3))
				.andExpect(jsonPath("$.ajustesDePresenca.2026").doesNotExist());
	}

	@Test
	void presencasDeQuemNuncaJogouPeloBotContamComoJogou() throws Exception {
		Jogador adm = admin("Adm", "5511900000001", 6, Posicao.M);
		Jogador antigo = membro("Antigo", "5511900000009", 7, Posicao.Z);
		editar(antigo, null, 12).andExpect(status().isOk());

		texto(adm, "#ranking");
		assertThat(ultimaMensagem()).isEqualTo("🏆 Ranking de habilidade\n🥇 Antigo — 7.0");
	}

	private ResultActions editar(Jogador jogador, Double skill, int presencas)
			throws Exception {
		return mvc.perform(put("/api/painel/jogadores/{id}", jogador.id()).with(jwt())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"nome":"%s","apelido":"%s","whatsapp":"%s","posicoes":["%s"],"skillBase":%s,
						 "skill":%s,"presencasNoAno":%d}
						""".formatted(jogador.nome(), jogador.apelido(), jogador.whatsapp(),
						jogador.posicoes().getFirst(), jogador.skillBase(), skill, presencas)));
	}
}
