from __future__ import annotations

from collections import defaultdict, deque


class TaskDependencyGraph:
    def __init__(self) -> None:
        self._deps: dict[str, set[str]] = defaultdict(set)

    def add_task(self, task_id: str, depends_on: tuple[str, ...] = ()) -> None:
        self._deps[task_id].update(depends_on)
        for dep in depends_on:
            self._deps.setdefault(dep, set())

    def blockers(self, task_id: str, completed: set[str]) -> set[str]:
        return self._deps.get(task_id, set()) - completed

    def ready(self, completed: set[str]) -> list[str]:
        return sorted(
            task_id
            for task_id, deps in self._deps.items()
            if task_id not in completed and deps <= completed
        )

    def topological_order(self) -> list[str]:
        indegree = {task: len(deps) for task, deps in self._deps.items()}
        children: dict[str, set[str]] = defaultdict(set)
        for task, deps in self._deps.items():
            for dep in deps:
                children[dep].add(task)

        queue = deque(sorted(task for task, degree in indegree.items() if degree == 0))
        ordered: list[str] = []
        while queue:
            node = queue.popleft()
            ordered.append(node)
            for child in sorted(children[node]):
                indegree[child] -= 1
                if indegree[child] == 0:
                    queue.append(child)

        if len(ordered) != len(indegree):
            raise ValueError("dependency cycle detected")
        return ordered
