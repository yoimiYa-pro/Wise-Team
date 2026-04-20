package com.teampm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

//自动配置数据源，Web，Security等，并把Bean都注册进容器
@SpringBootApplication
//接口型 Mapper都在com.teampm.mapper包里
@MapperScan("com.teampm.mapper")
//打开 Spring 的定时任务能力
@EnableScheduling
public class TeamPerformanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamPerformanceApplication.class, args);
    }
}
