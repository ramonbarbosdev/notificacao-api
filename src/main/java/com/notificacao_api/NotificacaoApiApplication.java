package com.notificacao_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.notificacao_api.config.DotenvLoader;

@SpringBootApplication
public class NotificacaoApiApplication {

	public static void main(String[] args) {
		DotenvLoader.init();
		SpringApplication.run(NotificacaoApiApplication.class, args);
	}

}
