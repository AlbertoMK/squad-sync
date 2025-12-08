package com.squadsync.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SquadSyncApplication {

	public static void main(String[] args) {
		SpringApplication.run(SquadSyncApplication.class, args);
	}

}
