package com.siga.siga_iea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SigaIeaApplication {

	public static void main(String[] args) {
		SpringApplication.run(SigaIeaApplication.class, args);
	}

}
