package com.bank.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class BankMqAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankMqAgentApplication.class, args);
    }
}
