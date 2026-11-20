package com.phivealpha;

import com.phivealpha.screening.LiquidityPolicy;
import com.phivealpha.screening.news.NewsScoringPolicy;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;

@EnableConfigurationProperties({LiquidityPolicy.class, NewsScoringPolicy.class})
@SpringBootApplication(exclude = R2dbcAutoConfiguration.class)
public class PhiveAlphaApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(PhiveAlphaApplication.class);
        application.setDefaultProperties(Map.of("spring.config.name", "phivealpha"));
        application.run(args);
    }
}
