package com.exchange.exchangeratesystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ExchangeRateSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExchangeRateSystemApplication.class, args);
    }
}
