package com.bank.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Transfer Service microservice.
 *
 * <p>Implements US-001 (happy path intra-bank THB transfer) and US-003
 * (idempotency). Saga is single-service in v1 (account-service client stubbed).
 */
@SpringBootApplication
@EnableScheduling
public class TransferServiceApplication {

    public static void main(final String[] args) {
        SpringApplication.run(TransferServiceApplication.class, args);
    }
}
