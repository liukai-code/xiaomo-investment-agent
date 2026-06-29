package com.itlk.myclaudecode.workflow.engine;

import com.itlk.myclaudecode.workflow.state.WorkflowState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class WorkflowGraph {

    private final Map<String, WorkflowNode> nodes = new LinkedHashMap<>();
    private final List<WorkflowEdge> edges = new ArrayList<>();
    private String startNode;

    public WorkflowGraph addNode(WorkflowNode node) {
        nodes.put(node.name(), node);
        return this;
    }

    public WorkflowGraph addEdge(String from, String to) {
        edges.add(new WorkflowEdge(from, to, null));
        return this;
    }

    public WorkflowGraph addEdge(String from, String to, Predicate<WorkflowState> condition) {
        edges.add(new WorkflowEdge(from, to, condition));
        return this;
    }

    public WorkflowGraph setStart(String nodeName) {
        this.startNode = nodeName;
        return this;
    }

    public List<WorkflowNode> resolveExecutionPath(WorkflowState state) {
        List<WorkflowNode> path = new ArrayList<>();
        String current = startNode;
        while (current != null) {
            WorkflowNode node = nodes.get(current);
            if (node == null) {
                break;
            }
            path.add(node);
            String next = null;
            for (WorkflowEdge edge : edges) {
                if (edge.fromNode().equals(current)) {
                    if (edge.condition() == null || edge.condition().test(state)) {
                        next = edge.toNode();
                        break;
                    }
                }
            }
            current = next;
        }
        return path;
    }
}
