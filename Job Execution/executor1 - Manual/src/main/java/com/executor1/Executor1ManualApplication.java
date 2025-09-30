package com.executor1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Executor1ManualApplication {

	public static void main(String[] args) {
		SpringApplication.run(Executor1ManualApplication.class, args);
	}

//	@Bean
//	CommandLineRunner run(RedisPriorityQueue queue) {
//		return args -> {
//			queue.addJobToDependency("jobparser1", "job1", LocalDateTime.now().plusMinutes(1));
//			queue.addJobToDependency("jobparser1", "job2", LocalDateTime.now().plusMinutes(2));
//			queue.addJobToDependency("jobparser2", "job3", LocalDateTime.now().plusMinutes(3));
//
//			System.out.println(queue.getJobsAsObjects("jobparser1"));
//			System.out.println(queue.getJobsAsObjects("jobparser2"));
//		};
//	}
}

