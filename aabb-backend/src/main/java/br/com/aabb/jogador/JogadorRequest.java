package br.com.aabb.jogador;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados do cadastro de um membro.
 *
 * @param whatsapp número internacional só com dígitos (ex: 5511999999999)
 * @param posicoes       a primeira é a posição principal
 * @param skill          skill atual definida pelo admin (opcional; ausente = não altera)
 * @param presencasNoAno total de presenças no ano atual definido pelo admin (opcional; ausente = não altera)
 */
public record JogadorRequest(
		@NotBlank @Size(max = 80) String nome,
		@NotBlank @Size(max = 30) String apelido,
		@NotBlank @Pattern(regexp = "\\d{10,15}", message = "use só dígitos, com DDI e DDD") String whatsapp,
		@NotEmpty List<@NotNull Posicao> posicoes,
		@NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double skillBase,
		Boolean admin,
		@DecimalMin("0.0") @DecimalMax("10.0") Double skill,
		@Min(0) @Max(999) Integer presencasNoAno) {

	/** Administrador pode usar os comandos exclusivos de admin no WhatsApp (padrão: não). */
	public boolean ehAdmin() {
		return Boolean.TRUE.equals(admin);
	}
}
