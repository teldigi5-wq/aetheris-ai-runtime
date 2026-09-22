package io.aetheris.orchestrator.model;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProviderRateLimitRepository extends JpaRepository<ProviderRateLimitEntity,String>{}
