package ch.resear.thiriot.knime.bayesiannetworks.lib.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.MoralGraph;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;

public final class EliminationOrderBestFirstSearch {

	public static final class NodeExplored implements Comparable<NodeExplored> {
		
		public final List<NodeCategorical> prefix;
		public final int width;
		
		/**
		 * subgraph which results of applying prefix to the original graph
		 */
		public final MoralGraph subgraph;
		
		/**
		 * lower bound on the treewidth of subgraph
		 */
		public final int lowerbound;

		public final int max;
		
		public NodeExplored(List<NodeCategorical> prefix, int width, MoralGraph subgraph, int lowerbound) {
			this.prefix = prefix;
			this.width = width;
			this.subgraph = subgraph;
			this.lowerbound = lowerbound;
			this.max = Math.max(lowerbound, width);
		}


		@Override
		public int compareTo(NodeExplored o) {
			return this.max - o.max;
		}


		@Override
		public boolean equals(Object obj) {
			try {
				NodeExplored other = (NodeExplored)obj;
				return subgraph.variables().equals(other.subgraph.variables()) && prefix.equals(other.prefix);
			} catch (ClassCastException e) {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return subgraph.variables().hashCode();
		}
	
		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer(); 
			sb.append("prefix [").append(prefix.stream().map(v->v.name).collect(Collectors.joining(","))).append("]");
			sb.append(" / graph {");
			sb.append(subgraph.variables().stream().map(v->v.name).collect(Collectors.joining(","))).append("}");
			sb.append(" (width:").append(width).append(", lowerbound:").append(lowerbound);
			return sb.toString();
		}
		
	}
	
	protected Map<Set<NodeCategorical>,NodeExplored> openList = new HashMap<>();
	protected SortedSet<NodeExplored> openListSorted = new TreeSet<>();

	protected Map<Set<NodeCategorical>,NodeExplored> closedList = new HashMap<>();
	
	private final ILogger logger;
	private final MoralGraph g;

	private long totalExplored =  0;
	
	private EliminationOrderBestFirstSearch(ILogger logger, MoralGraph g) {
		this.logger = logger;
		this.g = g;
	}
	

	private void addOpenList(NodeExplored n) {

		openList.put(n.subgraph.variables(), n);
		openListSorted.add(n);
		
	}
	
	private void removeOpenList(NodeExplored n) {
		openList.remove(n);
		openListSorted.remove(n);
	}
	
	/**
	 * Returns an optimal elimination order using the deep first search algorithm
	 * (see algo 19 named DFS_OEO Darwiche p291)
	 * @return
	 */
	protected List<NodeCategorical> computeEliminationOrder() {
		
		logger.info("searching for elimination order in " + g.variables().stream().map(v -> v.name).collect(Collectors.joining(",")));

		addOpenList(new NodeExplored(Collections.emptyList(), 0, g, g.getLowerBoundFromClique()));
		
		totalExplored =  0;
		
		while (!openList.isEmpty()) {
			
			// TODO is it too late ? 
			
			// take the best node so far
			NodeExplored node = openListSorted.first();
			removeOpenList(node);
			
			if (logger.isDebugEnabled())
				logger.debug("exploring best elimination orders based on " + node);


			// maybe this one is the best already ? 
			if (node.subgraph.isEmpty()) {
				logger.info("found an optimal elimination order having width"+ 
						node.width+":"+
						node.prefix.stream().map(v -> v.name).collect(Collectors.joining(","))+"("+ 
						totalExplored+" iterations)"
						);
				return node.prefix;
			}
			
			// or not... :-/
			for (NodeCategorical n: node.subgraph.variables()) {
				
				if (logger.isDebugEnabled())
					logger.debug("we might add node "+n+" to "+node.prefix);

				totalExplored++;
				
				List<NodeCategorical> prefix2 = new ArrayList<>(node.prefix.size()+1);
				prefix2.addAll(node.prefix);
				prefix2.add(n);
				
				MoralGraph g2 = node.subgraph.clone();
				g2.remove(n);
				
				int width2 = Math.max(node.subgraph.getNeighboors(n), node.width);
				
				int lowerbound2 = g2.getLowerBoundFromClique();
				
				NodeExplored alreadyExplored = openList.get(g2.variables());
				if (alreadyExplored !=null) {
					if (width2 < alreadyExplored.width) {

						if (logger.isDebugEnabled())
							logger.debug("we already explored "+g2.variables()
											+" but we found a better width "+width2
											+" (instead of "+alreadyExplored.width+"); keeping this better solution");
						openList.remove(alreadyExplored);
						addOpenList(new NodeExplored(
								prefix2, 
								width2, 
								g2, 
								lowerbound2
								));
						
					} else {
						if (logger.isDebugEnabled())
						logger.debug("we already explored "+g2.variables()+" with a better width "+alreadyExplored.width+" (instead of "+width2+"); keeping this old solution");
					}
				} else {
					alreadyExplored = closedList.get(g2.variables());
					if (alreadyExplored == null) {
						// its the first time we visited this; store that for later memory
						addOpenList(new NodeExplored(
								prefix2, 
								width2, 
								g2, 
								lowerbound2
								));
					}
					
				}
			
				
				
			}
			
			// we processed this node; consider it closed. 
			closedList.put(node.subgraph.variables(),node);
		}
		
		throw new RuntimeException("oops, we where not able to find any optimal elimination order...");
		
		
	}
	

	
	public static List<NodeCategorical> computeEliminationOrder(
			ILogger logger, 
			CategoricalBayesianNetwork bn) {
		
		EliminationOrderBestFirstSearch dfs = new EliminationOrderBestFirstSearch(logger, new MoralGraph(bn));
		return dfs.computeEliminationOrder();
	}

	
	public static List<NodeCategorical> computeEliminationOrder(
			ILogger logger, 
			CategoricalBayesianNetwork bn, Set<NodeCategorical> consideredNodes) {
		
		EliminationOrderBestFirstSearch dfs = new EliminationOrderBestFirstSearch(logger, new MoralGraph(bn, consideredNodes));
		return dfs.computeEliminationOrder();
	}

}
