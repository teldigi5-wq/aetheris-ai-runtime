package io.aetheris.orchestrator.stage32;

import java.util.*;

public final class WorkflowComposer {
    public List<String> compose(List<WorkflowStep> steps) {
        Map<String, WorkflowStep> byId = new LinkedHashMap<>();
        for (WorkflowStep step : steps) if (byId.put(step.id(), step) != null) throw new IllegalArgumentException("Duplicate workflow step: " + step.id());
        for (WorkflowStep step : steps) for (String dependency : step.dependsOn()) if (!byId.containsKey(dependency)) throw new IllegalArgumentException("Unknown dependency: " + dependency);

        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Set<String>> children = new HashMap<>();
        byId.keySet().forEach(id -> { indegree.put(id, 0); children.put(id, new TreeSet<>()); });
        for (WorkflowStep step : steps) for (String dep : step.dependsOn()) { indegree.merge(step.id(), 1, Integer::sum); children.get(dep).add(step.id()); }
        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        List<String> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.remove(); ordered.add(id);
            for (String child : children.get(id)) if (indegree.merge(child, -1, Integer::sum) == 0) ready.add(child);
        }
        if (ordered.size() != byId.size()) throw new IllegalArgumentException("Workflow dependency cycle detected");
        return List.copyOf(ordered);
    }
}
