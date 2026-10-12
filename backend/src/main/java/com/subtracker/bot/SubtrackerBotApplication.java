package com.subtracker.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SubtrackerBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(SubtrackerBotApplication.class, args);
	}

}
