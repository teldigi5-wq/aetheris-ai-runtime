package io.aetheris.orchestrator.career;import java.util.Map;public interface CareerEvidenceConnector {String id();boolean readOnly();boolean available();Map<String,Object> fetchVerifiedEvidence();}
