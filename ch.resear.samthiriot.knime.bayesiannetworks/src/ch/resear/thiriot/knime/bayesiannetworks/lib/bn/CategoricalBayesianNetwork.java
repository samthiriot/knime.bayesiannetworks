package ch.resear.thiriot.knime.bayesiannetworks.lib.bn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.knime.core.node.ExecutionContext;

import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.LogIntoJavaLogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.Factor;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.InferencePerformanceUtils;

public class CategoricalBayesianNetwork extends BayesianNetwork<NodeCategorical> {

	protected Map<NodeCategorical,Factor> node2factor = new ConcurrentHashMap<>();
	
	public CategoricalBayesianNetwork(ILogger logger, String name) {
		super(logger, name);

	}
	
	public CategoricalBayesianNetwork(String name) {
		super(LogIntoJavaLogger.getLogger(CategoricalBayesianNetwork.class), name);

	}
	
	
	public CategoricalBayesianNetwork clone() {
		return CategoricalBayesianNetwork.readFromXMLBIF(this.logger, this.getAsXMLString());
	}
	
	/**
	 * returns the nodes in their order for browsing them from root to leafs.
	 * For categorical networks, returns first the biggest domains.
	 * This provides stability in the order and facilitates the parallelism based on domain 
	 * partition.
	 * @return
	 */
	@Override
	public List<NodeCategorical> enumerateNodes() {
		
		List<NodeCategorical> toProcess = new LinkedList<>(nodes);

		// first sort the nodes with biggest domains first
		// this way we will present first the roots with the biggest domains
		Collections.sort(toProcess, new Comparator<NodeCategorical>() {

			@Override
			public int compare(NodeCategorical o1, NodeCategorical o2) {
				return Integer.compare(o1.getDomainSize(), o2.getDomainSize());
			}
		});
		return enumerateNodes(toProcess);
	}
	
	@Override
	public void notifyNodesChanged() {
		super.notifyNodesChanged();
		node2factor.clear();
		cacheNodesRankedPerZero = null; 
	}
	
	/**
	 * Returns the node as a factor, or the corresponding value 
	 * @param n
	 * @return
	 */
	public Factor getFactor(NodeCategorical n) {
		
		// cached ? 
		Factor res = node2factor.get(n);
		
		if (res == null) {
			res = n.asFactor();
			node2factor.put(n, res);
		}
		
		return res;
	}

	List<NodeCategorical> cacheNodesRankedPerZero = null;
	public List<NodeCategorical> enumerateVariablesPerZeros() {
		
		if (cacheNodesRankedPerZero != null)
			return cacheNodesRankedPerZero;

		cacheNodesRankedPerZero = rankVariablesPerZeros(getNodes());

		return cacheNodesRankedPerZero;
	}
		
	/**
	 * ranks the nodes per count of zeros in the CPT 
	 * @param all
	 * @return
	 */
	
	protected List<NodeCategorical> rankVariablesPerZeros(Collection<NodeCategorical> all) {
	

		//logger.info("should rank variables {}", all);
		List<NodeCategorical> res = new ArrayList<>(all);
		
		Map<NodeCategorical,Double> node2proportionZeros = res.stream().collect(
				Collectors.toMap(
	    				n -> n, 
	    				n -> (double)n.getCountOfZeros()/n.getCardinality() ));
		
		res.sort(new Comparator<NodeCategorical>() {
			
			@Override
			public int compare(NodeCategorical o1, NodeCategorical o2) {
				Double proportionOfZeros1 = node2proportionZeros.get(o1);
				Double proportionOfZeros2 = node2proportionZeros.get(o2);
				// first compare on the proportion of zeros: the higher the better
				int r = - proportionOfZeros1.compareTo(proportionOfZeros2);
				// then, if equal, compare on size (why?)
				if (r==0)
					r = o1.getCardinality()-o2.getCardinality();
				return r;
			}
    		
		});
		
		//logger.info("ranked variables {}", res);

		return res;
	}


	/**
	 * reads a bayesian network from a String containing a network description in XMLBIF format. 
	 * @param xmlStr
	 * @return
	 */
	public static CategoricalBayesianNetwork readFromXMLBIF(ILogger logger, String xmlStr) {
		
        Document document;
		try {
			document = DocumentHelper.parseText(xmlStr);
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IllegalArgumentException("invalid XML BIF format", e);
		}

    	final String networkName = document.selectSingleNode("/BIF/NETWORK/NAME").getText().trim();

		// add them all into a Bayesian net
    	CategoricalBayesianNetwork bn = new CategoricalBayesianNetwork(logger, networkName);
		
        // read the variables
		Map<String,NodeCategorical> id2node = new HashMap<>();
		{
	        List<?> variables = document.selectNodes("/BIF/NETWORK/VARIABLE");
	        for (Iterator<?> iterVars = variables.iterator(); iterVars.hasNext(); ) {
	            
	        	Node nodeVariable = (Node)iterVars.next();
	        	
	        	final String variableName = nodeVariable.selectSingleNode("./NAME").getText().trim();
	        	
	        	NodeCategorical n = new NodeCategorical(bn, variableName);
	        	id2node.put(variableName, n);
	        	
	        	for (Object nodeRaw : nodeVariable.selectNodes("./OUTCOME")) {
	        		Node nodeOutcome = (Node)nodeRaw;
	        		n.addDomain(nodeOutcome.getText().trim());
	        	}
	        
	        }
		}
		
		// read the definitions
		{
	        List<?> definitions = document.selectNodes("/BIF/NETWORK/DEFINITION");
	        for (Iterator<?> iterDefinition = definitions.iterator(); iterDefinition.hasNext(); ) {
	            
	        	Node nodeDefinition = (Node)iterDefinition.next();
	        	
	        	// decode to which variable this definition is related
	        	final String variableName = nodeDefinition.selectSingleNode("./FOR").getText().trim();
	        	final NodeCategorical n = id2node.get(variableName);
	        	
	        	// find and add the parents
	        	for (Object nodeGivenRaw: nodeDefinition.selectNodes("./GIVEN")) {
	        		Node nodeGiven = (Node)nodeGivenRaw;
	        		NodeCategorical np = id2node.get(nodeGiven.getText().trim());
	        		if (np == null)
	        			throw new IllegalArgumentException("unknown node "+nodeGiven.getText().trim());
	        		n.addParent(np);
	        	}
	        	
	        	// now set the probabilities
	        	final String tableContent = nodeDefinition.selectSingleNode("./TABLE").getText().trim();
	        	List<Double> values = new LinkedList<>();
	        	for (String tok : tableContent.split("[ \t]+")) {
	        		try {
	        			//System.out.println("decoding "+tok+" to "+new Double(tok));
	        			values.add(Double.parseDouble(tok));
	        		} catch (NumberFormatException e) {
	        			throw new IllegalArgumentException("error while parsing this value as a BigDecimal: "+tok,e);
	        		}
	        	}
	        	Double[] valuesA = new Double[values.size()];
	        	values.toArray(valuesA);
	        	n.setProbabilities(ArrayUtils.toPrimitive(valuesA));
	        
	        }
		
		}
		
		bn.addAll(id2node.values());

		// check it is ok
		if (!bn.isValid()) {
			
			throw new IllegalArgumentException("the bn is not valid: "+
				bn.collectInvalidProblems()
				.entrySet()
				.stream()
				.map(k2v -> (String)(k2v.getKey().getName() + ":" + k2v.getValue()))
				.collect(Collectors.joining(", ")));
		}
		
		return bn;
	}
	
	/**
	 * Loads a network from a file which contains a Bayesian network .
	 * @param f
	 * @return
	 */
	public static CategoricalBayesianNetwork loadFromXMLBIF(ILogger logger, File f) {
		
		if (logger.isDebugEnabled())
			logger.debug("reading a CategoricalBayesianNetwork from XML BIF file " + f);

		try {
			return loadFromXMLBIF(logger, FileUtils.readFileToString(f, Charset.defaultCharset()));
		} catch (IOException e) {
			throw new IllegalArgumentException("unable to read file "+f, e);
		}
		
	}
	
	public static CategoricalBayesianNetwork loadFromXMLBIF(File f) {
		
		return loadFromXMLBIF(LogIntoJavaLogger.getLogger(CategoricalBayesianNetwork.class), f);
	}
	
	public static CategoricalBayesianNetwork loadFromXMLBIF(ILogger logger, String s) {

			return readFromXMLBIF(logger, s);
		
	}

	public static CategoricalBayesianNetwork loadFromXMLBIF(String s) {

			return readFromXMLBIF(LogIntoJavaLogger.getLogger(CategoricalBayesianNetwork.class), s);
		
	}
	
	public Set<NodeCategorical> getAllAncestors(NodeCategorical n) {

		Set<NodeCategorical> res = new HashSet<>(n.getParents());
		res.add(n);
		
		Set<NodeCategorical> toProcess = new HashSet<>(n.getParents());
		Set<NodeCategorical> processed = new HashSet<>();
		
		while (!toProcess.isEmpty()) {
			Iterator<NodeCategorical> it = toProcess.iterator();
			NodeCategorical c = it.next();
			it.remove();
			processed.add(c);
			res.addAll(c.getParents());
			toProcess.addAll(c.getParents());
			toProcess.removeAll(processed);
		}
		
		return res;
	}
	

	protected List<NodeCategorical> rankVariablesForMultiplication(Collection<NodeCategorical> nodes) {
		// by default, does nothing of interest.
		// should be overriden with something more smart.
		return new ArrayList<NodeCategorical>(nodes);
	}
	
	public IteratorCategoricalVariables iterateDomains() {
		
		return new IteratorCategoricalVariables(this.enumerateNodes());
	}
	
	public IteratorCategoricalVariables iterateDomains(Collection<NodeCategorical> nn) {
		if (!this.nodes.containsAll(nn))
			throw new IllegalArgumentException("some of these nodes "+nn+" do not belong this Bayesian network "+this.nodes);
		return new IteratorCategoricalVariables(nn);
	}

	public Stream<Map<NodeCategorical,String>> streamDomains(ExecutionContext ex) {
		SpliteratorCategoricalVariables spliterator = this.spliterateDomains(ex);
        return StreamSupport.stream(spliterator, false);
	}
	
	public Stream<Map<NodeCategorical,String>> streamDomains(Collection<NodeCategorical> nn, ExecutionContext ex) {
		SpliteratorCategoricalVariables spliterator = this.spliterateDomains(nn, ex);
        return StreamSupport.stream(spliterator, false);
	}
	
	public SpliteratorCategoricalVariables spliterateDomains(ExecutionContext ex) {
		return new SpliteratorCategoricalVariables(this.enumerateNodes(), ex);
	}
	
	
	public SpliteratorCategoricalVariables spliterateDomains(Collection<NodeCategorical> nn,ExecutionContext ex) {
		if (!this.nodes.containsAll(nn))
			throw new IllegalArgumentException("some of these nodes "+nn+" do not belong this Bayesian network "+this.nodes);
		return new SpliteratorCategoricalVariables(nn, ex);
	}
	
	public Stream<Map<NodeCategorical,String>> parallelStreamDomains(ExecutionContext ex) {
		SpliteratorCategoricalVariables spliterator = this.spliterateDomains(ex);
		return StreamSupport.stream(spliterator, true);
	}
	
	public Stream<Map<NodeCategorical,String>> parallelStreamDomains(Collection<NodeCategorical> nn, ExecutionContext ex) {
		SpliteratorCategoricalVariables spliterator = this.spliterateDomains(nn, ex);
		return StreamSupport.stream(spliterator, true);
	}
	
	/**
	 * For a list of variable and values, computes the corresponding joint probability.
	 * Takes as an input all the 
	 * @param node2value
	 * @param node2probabilities 
	 * @return
	 */
	public double jointProbability(
			Map<NodeCategorical,String> node2value, 
			Map<NodeCategorical,String> evidence) {
		
		/*BigDecimal res = getCached(evidence, node2value);
		if (res != null)
			return res;
		*/
		if (logger.isDebugEnabled())
			logger.debug("computing joint probability p(" + node2value + ")");

		double res = 1.;
		
		for (NodeCategorical n: rankVariablesPerZeros(node2value.keySet())) {
			
			// optimisation: compute first the nodes having a lot of zeros to stop computation asap
			String v = node2value.get(n);
			
			if (!node2value.keySet().containsAll(n.getParents())) {
				throw new InvalidParameterException("wrong parameters: expected values for each parent of "+n+": "+n.getParents());
			}
			double p;
			
			// find the probability to be used
			if (evidence.containsKey(n)) {
				if (evidence.get(n).equals(v)) {
					p = 1.;
				} else {
					p = 0.;
				}
			} else if (n.hasParents()) {
				// if there are parent values, let's create the probability by reading the CPT 
				Map<NodeCategorical,String> s = n.getParents().stream().collect(Collectors.toMap(p2 -> p2, p2 -> node2value.get(p2)));
				p = n.getProbability(v, s);
			} else {
				// no parent values. Let's use the ones of our CPT
				p = n.getProbability(v);
			}
			if (logger.isDebugEnabled())
				logger.debug("p("+n.name+"="+v+")="+p);

			// use it
			if (p==0.) {
				// optimisation: stop if multiplication by 0
				res = 0.;
				break;
			} else if (p != 1) {
				// optimisation: multiply only if useful
				res = res* p;
				InferencePerformanceUtils.singleton.incMultiplications();
			}
			
		}
		
		if (logger.isDebugEnabled())
			logger.debug("computed joint probability p("+node2value+")="+res);

		//storeCache(node2value, evidence, res);
		
		return res;
		
	}
	
	public double jointProbabilityFromFactors(Map<NodeCategorical,String> node2value) {
				
		Factor f = null;
		
		for (NodeCategorical n: enumerateVariablesPerZeros()) {
			Factor fn = n.asFactor();
			if (f == null)
				f = fn;
			else 
				f = f.multiply(fn);
		}
		
		return f.get(node2value);
	}

	/**
	 * Prunes the Bayesian network by removing the variable n, and updating all the probabilities for
	 * other nodes in the network. 
	 * @param node
	 */
	public void prune(NodeCategorical n) {
		// TODO !!!
	}


	/**
	 * Returns a map of variable (node) and value by parsing the string values
	 * @param sss
	 * @return
	 */
	public Map<NodeCategorical, String> toNodeAndValue(String... sss) {
		
		return this.toNodeAndValue(this.nodes, sss);
	}	
	
	/**
	 * Returns a map of variable (node) and value by parsing the string values
	 * @param sss
	 * @return
	 */
	public Map<NodeCategorical, String> toNodeAndValue(Collection<NodeCategorical> nodes, String... sss) {
		
		if (nodes != null && !this.nodes.containsAll(nodes))
			throw new IllegalArgumentException("Not all the nodes "+nodes+" are in this Bayesian network");
		if (nodes != null && sss.length != nodes.size()*2)
			throw new IllegalArgumentException("invalid keys and values");
		
		Map<NodeCategorical,String> n2s = new HashMap<>(nodes != null?nodes.size():this.nodes.size());
		for (int i=0; i<sss.length; i+=2) {
			NodeCategorical n = getVariable(sss[i]);
			if (n == null || (nodes != null && !nodes.contains(n)))
				throw new IllegalArgumentException("Unknown variable "+sss[i]);
			String v = sss[i+1];
			if (!n.getDomain().contains(v))
				throw new IllegalArgumentException("unknown value "+v+" for variable "+sss[i]);
			n2s.put(n, v);
		}
		return n2s;
	}	
	


}
