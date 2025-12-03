package com.BossLiftingClub.BossLifting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BossLiftingApplication {

	public static void main(String[] args) {
		SpringApplication.run(BossLiftingApplication.class, args);
	}

}
