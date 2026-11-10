package br.com.aabb.jogador;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface JogadorRepository extends MongoRepository<Jogador, String> {

	Optional<Jogador> findByWhatsapp(String whatsapp);

	List<Jogador> findByWhatsappIn(List<String> whatsapps);

	Optional<Jogador> findByLids(String lid);
}
