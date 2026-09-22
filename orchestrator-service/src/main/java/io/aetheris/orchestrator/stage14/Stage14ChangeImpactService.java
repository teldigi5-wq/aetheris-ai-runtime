package io.aetheris.orchestrator.stage14;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage14ChangeImpactService {
    public ChangeImpact assess(ChangeImpactRequest request) {
        if (request == null || request.changedComponents() == null || request.changedComponents().isEmpty()) {
            throw new IllegalArgumentException("At least one changed component is required");
        }
        Set<String> changed = normalizeSet(request.changedComponents());
        Set<String> critical = normalizeSet(request.criticalComponents() == null ? Set.of() : request.criticalComponents());
        Map<String, Set<String>> deps = normalizeGraph(request.dependencies());
        boolean cycle = hasCycle(deps);

        Map<String, Set<String>> reverse = new HashMap<>();
        deps.forEach((component, directDeps) -> directDeps.forEach(dep -> reverse.computeIfAbsent(dep, k -> new TreeSet<>()).add(component)));
        TreeSet<String> affected = new TreeSet<>(changed);
        ArrayDeque<String> queue = new ArrayDeque<>(changed);
        while (!queue.isEmpty()) {
            String node = queue.removeFirst();
            for (String downstream : reverse.getOrDefault(node, Set.of())) if (affected.add(downstream)) queue.addLast(downstream);
        }
        TreeSet<String> criticalAffected = new TreeSet<>(affected);
        criticalAffected.retainAll(critical);
        int score = Math.min(100, 20 + affected.size() * 5 + criticalAffected.size() * 25 + (cycle ? 40 : 0));
        String risk = score >= 80 ? "CRITICAL" : score >= 60 ? "HIGH" : score >= 35 ? "MEDIUM" : "LOW";
        List<String> blockers = cycle ? List.of("dependency graph contains a cycle and must be resolved before deployment") : List.of();
        return new ChangeImpact(cycle ? "BLOCKED" : "ANALYZED", Set.copyOf(affected), Set.copyOf(criticalAffected),
                score, risk, blockers, false);
    }

    private Map<String, Set<String>> normalizeGraph(Map<String, Set<String>> input) {
        if (input == null) return Map.of();
        TreeMap<String, Set<String>> out = new TreeMap<>();
        input.forEach((k, v) -> out.put(token(k), normalizeSet(v == null ? Set.of() : v)));
        return Collections.unmodifiableMap(out);
    }
    private Set<String> normalizeSet(Collection<String> input) {
        TreeSet<String> out = new TreeSet<>();
        for (String v : input) out.add(token(v));
        return Collections.unmodifiableSet(out);
    }
    private String token(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("component name is required");
        String v = value.trim();
        if (v.length() > 100 || !v.matches("[A-Za-z0-9._:/-]{1,100}")) throw new IllegalArgumentException("component name is invalid");
        return v;
    }
    private boolean hasCycle(Map<String, Set<String>> graph) {
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        Set<String> all = new HashSet<>(graph.keySet());
        graph.values().forEach(all::addAll);
        for (String node : all) if (dfs(node, graph, visiting, visited)) return true;
        return false;
    }
    private boolean dfs(String node, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(node)) return false;
        if (!visiting.add(node)) return true;
        for (String dep : graph.getOrDefault(node, Set.of())) if (dfs(dep, graph, visiting, visited)) return true;
        visiting.remove(node); visited.add(node); return false;
    }

    public record ChangeImpactRequest(Set<String> changedComponents, Map<String, Set<String>> dependencies,
                                      Set<String> criticalComponents) {}
    public record ChangeImpact(String status, Set<String> affectedComponents, Set<String> criticalAffected,
                               int riskScore, String riskLevel, List<String> blockers,
                               boolean externalActionAttempted) {}
}
