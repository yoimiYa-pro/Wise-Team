package com.teampm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.teampm.mapper")
@EnableScheduling
public class TeamPerformanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamPerformanceApplication.class, args);
    }
}
