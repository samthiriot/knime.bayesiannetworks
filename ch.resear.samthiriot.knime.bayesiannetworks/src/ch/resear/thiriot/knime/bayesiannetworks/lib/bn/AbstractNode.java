package ch.resear.thiriot.knime.bayesiannetworks.lib.bn;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractNode<N extends AbstractNode<N>> {

	public final String name;
	protected final BayesianNetwork<N> network;
	
	protected final Set<N> parents = new HashSet<>();
	protected final Map<String,N> name2parent = new HashMap<>();
	
	
	@SuppressWarnings("unchecked")
	public AbstractNode(BayesianNetwork<N> net, String name) {
		this.network = net;
		this.name = name;
		
		if (network != null)
			this.network.add((N) this);
	}

	public void addParent(N parent) {
		if (parents.contains(parent)) {
			throw new IllegalArgumentException("parent "+parent.getName()+" already part of the node's parents");
		}
		parents.add(parent);
		name2parent.put(parent.getName(), parent);
		if (network != null)
			network.notifyNodesChanged();
	}
	
	public Set<N> getParents() {
		return Collections.unmodifiableSet(parents);
	}
	
	public Set<N> getChildren() {
		
		return this.network.getChildren(this);
	}
	
	protected final N getParent(String lbl) {
		return name2parent.get(lbl);
	}
	
	public final boolean hasParents() {
		return !parents.isEmpty();
	}
	
	public final String getName() {
		return name;
	}
	
	public abstract boolean isValid();

	public abstract List<String> collectInvalidityReasons();
	
	public abstract void toXMLBIF(StringBuffer sb);
	
	public abstract void toBIF(StringBuffer sb);

	@SuppressWarnings("unchecked")
	public Collection<N> getAllAncestors() {
		return network.getAllAncestors((N) this);
	}

	public Collection<N> getAllChildren() {
		return network.getAllChildren((N) this);
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("p(").append(name);
		
		if (!parents.isEmpty()) {
			sb.append('|');
			sb.append(parents.stream().map(n -> n.name).collect(Collectors.joining(",")));
		}
		
		sb.append(")");
		
		return sb.toString();
	}

}
