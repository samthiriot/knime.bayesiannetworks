package ch.resear.thiriot.knime.bayesiannetworks.lib.bn;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class FiniteNode<N extends AbstractNode<N>> extends AbstractNode<N> {

	protected List<String> domain = new LinkedList<>();
	protected Map<Object,Integer> domain2index = new HashMap<>(30);
	
	public FiniteNode(BayesianNetwork<N> net, String name) {
		super(net, name);
	}


	/**
	 * (internal) Adds a value into the domain of the categorical variable with no verification. 
	 * @param vv
	 */
	private final void _addDomain(String vv) {
		domain2index.put(vv, domain.size());
		domain.add(vv);
	}
	
	/**
	 * Adds a value in the domain if it does not already exists with the same name, 
	 * and adapts the internal size for the content (thus loosing any data there before)
	 * @param vv
	 */
	public final void addDomain(String vv) {
		if (domain2index.containsKey(vv)) {
			throw new IllegalArgumentException(vv+" is already part of the domain");
		}
		this._addDomain(vv);
		adaptContentSize();
	}
	
	/**
	 * Adds several values in the domain and adapts internal storage .
	 * @param vvs
	 */
	public final void addDomain(String ... vvs) {
		
		// check params
		for (String vv : vvs) {
			if (domain.contains(vv)) {
				throw new IllegalArgumentException(vv+" is already part of the domain");
			}
		}
		
		// add values
		for (String vv : vvs) {
			_addDomain(vv);
		}
		
		// adapt cpt size
		adaptContentSize();
	}

	protected abstract void adaptContentSize();
	
	public final List<String> getDomain() {
		return Collections.unmodifiableList(domain);
	}
	
	public final String getDomain(int index) {
		return domain.get(index);
	}
	
	
	public final String getValueIndexed(int v) {
		return domain.get(v);
	}
	
	public final int getDomainIndex(String value) {
		try {
			return domain2index.get(value);
		} catch (NullPointerException e) {
			if (!domain2index.containsKey(value))
				throw new IllegalArgumentException("there is no value \""+value
						+"\" in variable "+this);
			throw e;
		}
	}

	public final int getDomainSize() {
		return domain.size();
	}
	
	/**
	 * Returns true if the domain contains this string
	 */
	public boolean contains(String vv) {
		boolean res = domain2index.containsKey(vv);
		
		if (!res) {
			System.err.println("domain of: "+getName()+" does not contains \""+vv+"\" = "+domain);
			System.err.println(
					vv.chars()
						.mapToObj(i -> Integer.toString(i))
						.collect(Collectors.joining("-"))
						);
			System.err.println("searching for \""+vv+"\"");
			for (Object o: domain2index.keySet()) {
				String s = (String)o;
				System.err.print("* \""+s+"\" ? ");
				System.err.print(s.equals(vv));
				System.err.print(" ");
				System.err.println(o.equals(vv));
				
				
				System.err.print("* [");
				System.err.println(s.chars()
						.mapToObj(i -> Integer.toString(i))
						.collect(Collectors.joining("-"))
						);

			}
		}
		return res;
	}
	
}
