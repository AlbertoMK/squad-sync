package com.squadsync.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
@EnableScheduling
public class SquadSyncApplication {

	@Value("${app.timezone:Europe/Madrid}")
	private String appTimezone;

	@PostConstruct
	public void init() {
		// Setting default timezone to ensure LocalDateTime.now() matches configured
		// timezone
		TimeZone.setDefault(TimeZone.getTimeZone(appTimezone));
		System.out.println("Application running in timezone: " + TimeZone.getDefault().getID());
	}

	public static void main(String[] args) {
		SpringApplication.run(SquadSyncApplication.class, args);
	}

}
