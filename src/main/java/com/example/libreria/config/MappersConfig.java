package com.example.libreria.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MappersConfig {
    @Bean
    public ModelMapper modelMapper(){
        return new ModelMapper();
    }
}
