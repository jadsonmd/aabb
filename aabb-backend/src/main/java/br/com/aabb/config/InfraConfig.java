package br.com.aabb.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

@Configuration
public class InfraConfig {

	@Bean
	Clock clock(AabbProperties properties) {
		return Clock.system(properties.agenda().zona());
	}

	/** Transações exigem o MongoDB em replica set (ver docker-compose). */
	@Bean
	MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
		return new MongoTransactionManager(factory);
	}
}
