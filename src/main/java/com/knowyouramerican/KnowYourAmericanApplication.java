package com.knowyouramerican;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowYourAmericanApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowYourAmericanApplication.class, args);
    }
}
