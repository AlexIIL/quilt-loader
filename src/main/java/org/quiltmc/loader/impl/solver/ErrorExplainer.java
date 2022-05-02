package org.quiltmc.loader.impl.solver;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.List;
import java.util.Map;

public final class ErrorExplainer {
	SimpleDirectedGraph<LoadOption, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
	public ErrorExplainer() {
	}

	public void addError(Map<MainModLoadOption, MandatoryModIdDefinition> roots, List<Rule> causes) {
		roots.forEach((option, definition) -> {
			graph.addVertex(option);
			for (Rule cause : causes) {
				if (cause.getNodesFrom().isEmpty()) {
					throw new IllegalStateException("No parents for " + cause);
				}

				for (LoadOption parent : cause.getNodesFrom()) {
					graph.addVertex(parent);
//					if (!option.equals(parent)) {
//						graph.addEdge(parent, option);
//					}

				}
				if (cause.isNode()) {
					for (LoadOption child : cause.getNodesTo()) {
						graph.addVertex(child);
						for (LoadOption parent : cause.getNodesFrom()) {
							graph.addEdge(child, parent);
						}
					}
				} else {
					throw new IllegalStateException("Not implemented yet");
				}


			}
		});
	}
}
