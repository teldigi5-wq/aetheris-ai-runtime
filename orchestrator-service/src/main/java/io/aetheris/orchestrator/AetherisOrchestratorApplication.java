package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.AgentCatalogProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AgentCatalogProperties.class)
public class AetherisOrchestratorApplication {
    public static void main(String[] args) {SpringApplication.run(AetherisOrchestratorApplication.class,args);}
}
