package com.medroute.nav.navigation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class NavigationConfiguration {
    @Bean
    public Clock navigationClock() {
        return Clock.systemUTC();
    }
}
