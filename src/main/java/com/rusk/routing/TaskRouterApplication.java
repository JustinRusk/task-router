package com.rusk.routing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // runs scheduled jobs such as the routing rule cache refresh
public class TaskRouterApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskRouterApplication.class, args);
	}

}
