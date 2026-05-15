package com.notificacao_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.notificacao_api.config.DotenvLoader;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class NotificacaoApiApplication {

	public static void main(String[] args) {
		DotenvLoader.init();
		SpringApplication.run(NotificacaoApiApplication.class, args);
	}

}
