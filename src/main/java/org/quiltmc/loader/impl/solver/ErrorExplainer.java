package org.quiltmc.loader.impl.solver;

import java.util.List;
import java.util.Map;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

public final class ErrorExplainer {

	static final String ROOT = "MANDATORY";

	SimpleDirectedGraph<Object, OverrideEdge> graph = new SimpleDirectedGraph<>(OverrideEdge.class);

	public ErrorExplainer() {}

	public void addError(Map<MainModLoadOption, MandatoryModIdDefinition> roots, List<Rule> causes) {

		graph.addVertex(ROOT);

		for (MandatoryModIdDefinition def : roots.values()) {
			addRule(def);
			graph.addEdge(ROOT, def);
		}

		for (Rule rule : causes) {
			addRule(rule);
		}
	}

	private void addRule(Rule rule) {
		// if (rule.getNodesFrom().isEmpty()) {
		// throw new IllegalStateException("No parents for " + rule);
		// }

		graph.addVertex(rule);

		for (LoadOption parent : rule.getNodesFrom()) {
			graph.addVertex(parent);
			graph.addEdge(parent, rule);

		}

		for (LoadOption child : rule.getNodesTo()) {
			graph.addVertex(child);
			graph.addEdge(rule, child);
		}
	}

	static class OverrideEdge extends DefaultEdge {
		@Override
		public String toString() {
			return "";
		}
	}
}
