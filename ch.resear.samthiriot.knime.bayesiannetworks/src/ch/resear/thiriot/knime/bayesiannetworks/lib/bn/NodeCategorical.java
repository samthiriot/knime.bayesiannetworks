package ch.resear.thiriot.knime.bayesiannetworks.lib.bn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringEscapeUtils;

import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.LogIntoJavaLogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.Factor;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.InferencePerformanceUtils;

public final class NodeCategorical extends FiniteNode<NodeCategorical> {

	private final ILogger logger;
	
	protected NodeCategorical[] parentsArray = new NodeCategorical[0];
	protected Map<NodeCategorical,Integer> parent2index = new HashMap<>(50);
	
	private Integer countZeros = null;
		
	private double[] content;
	
	/**
	 * stores multipliers to compute indices
	 */
	private int[] multipliers; 

	protected final CategoricalBayesianNetwork cNetwork;
	
	/**
	 * Returns a human readible repreentation of a domain, assuming that displaying more than 20 values
	 * does not make sense.
	 * So it returns [a, b, c, ...] in case of too many elements
	 * @param domain
	 * @return
	 */
	public static String getStrRepresentationOfDomain(List<String> domain) {
		
		// if only a few values: it's easy!
		if (domain.size() <= 20)
			return domain.toString();
		
		List<String> subdomain = new ArrayList<>(domain.subList(0, 19));
		subdomain.add("...");
		return subdomain.toString();
	}
	
	public NodeCategorical(CategoricalBayesianNetwork net, String name) {
		
		super(net, name);
		
		if (net == null || net.logger == null)
			logger = LogIntoJavaLogger.getLogger(NodeCategorical.class);
		else 
			logger = net.logger;
		
		this.cNetwork = net;
	}
	
	public final double[] getContent() {
		return Arrays.copyOf(content, content.length);
	}
	
	public final CategoricalBayesianNetwork getNetwork() {
		return cNetwork;
	}
	
	public Integer getCountOfZeros() {
		if (countZeros == null) 
			computeCountOfZeros();
		return countZeros;
	}

	private void computeCountOfZeros() {
		int count = 0;
		for (double x: content) {
			if (x == 0)
				count++;
		}
		this.countZeros = count;
	}
	
	
	protected int getParentsCardinality() {
		return parents.stream().mapToInt(NodeCategorical::getDomainSize).reduce(1, Math::multiplyExact);
	}
	
	/**
	 * returns the number of values, aka size of CPT
	 * @return
	 */
	public int getCardinality() {
		return domain.size()*getParentsCardinality();
	}
	
	@Override
	public void addParent(NodeCategorical parent) {
		super.addParent(parent);
		parentsArray = Arrays.copyOf(parentsArray, parentsArray.length + 1);
		parentsArray[parentsArray.length-1] = parent;
		adaptContentSize();
		parent2index.put(parent, parent2index.size());
	}



	protected final int _getIndex(String ourValue, Object ... parentAndValue) {
		
		return _getIndex(getDomainIndex(ourValue), _getParentIndices(parentAndValue));
	}
	

	protected final int _getIndex(String ourValue, Map<NodeCategorical,String> parent2Value) {
		
		return _getIndex(getDomainIndex(ourValue), _getParentIndices(parent2Value));
	}
	
	
	protected void adaptContentSize() {
		
		// TODO reactivate logger.trace("resizing CPT for node {} for domain {} ({}) and parents card {} => {}", name, domain.size(), domain, getParentsCardinality(), getCardinality());

		final int card = getCardinality();
		//final int ndim = 1+parents.size();
				
		// TODO only if it changed ?
		
		// TODO keep the old array, reuse its probas, etc.
		content = new double[card];
		
		// adapt the association domain / size
		
		// adapt multipliers to be able to fetch data later
	    multipliers = new int[parents.size()];
	    for (int idxParent = 0; idxParent<parentsArray.length; idxParent++) {
	    	int currentfactor = getDomainSize();
		    for (int j=idxParent+1; j<parentsArray.length; j++) {
		    	currentfactor *= parentsArray[j].getDomainSize();
		    }
	    	multipliers[idxParent] = currentfactor;
	    }
	   // multipliers[multipliers.length-1] = 1; // currentfactor*getDomainSize();
	    
	 // TODO reactivate logger.trace("=> novel index multipliers {}", multipliers);

	    
	}

	/**
	 * returns the flatten index for the value of index ourDomainIdx in our domain and the given indices for parents
	 * 
	 * @see http://codinghighway.com/2014/01/27/c-multidimensional-arrays/
	 * 
	 * @param ourDomainIdx
	 * @param parentIndices
	 * @return
	 */
	protected int _getIndex(int ourDomainIdx, int... parentIndices) {
		
		// TODO reactivate 
		/*
		logger.trace(
				"computing flatten index for {}={}, parents {} in {} with multipliers {}", 
				name, 
				domain.get(ourDomainIdx), 
				parentIndices, 
				parentsArray,
				multipliers
				);
*/
		
		int idx = 0;
		
		// add our index
		idx += ourDomainIdx;	
		
		// add the indices of parents
		for (int idxP = 0; idxP < parentIndices.length; idxP++) {
			idx += parentIndices[idxP] * multipliers[idxP];
	    }
		
		// TODO reactivate logger.trace("computed flatten index {} for {} {} in {} values {} with multipliers {}", idx, name, ourDomainIdx, domain, parentIndices, multipliers);

		return idx;
	}
	
	public void setProbabilities(double p, String key, Object ... parentAndValue) {
		countZeros = null;
		content[_getIndex(key, parentAndValue)] = p;
	}
	
	public void setProbabilities(double p, String key, Map<NodeCategorical,String> parent2Value) {
		countZeros = null;
		content[_getIndex(key, parent2Value)] = p;
	}
	
	public void setProbabilities(double[] values) {
		if (values.length != getParentsCardinality()*getDomainSize())
			throw new IllegalArgumentException("wrong size for the content");
		countZeros = null;
		this.content = values;
	}


	public double getProbability(String key, Object ... parentAndValue) {
		return content[_getIndex(key, parentAndValue)];
	}
	

	public double getProbability(String key, Map<NodeCategorical,String> parent2Value) {
		return content[_getIndex(key, parent2Value)];
	}
	

	
	/**
	 * Returns the probability stored for our domain value "key" and the parents domain values "values"
	 * @param key
	 * @param values
	 * @return
	 */
	protected double getProbability(int key, int[] values) {
		//logger.trace("get probability for key {} in {} and values {} in {}", key, domain, values, parentsArray.length > 0 ? parentsArray[0].getDomain(): "");
		//try {
			return content[_getIndex(key, values)];
		//} catch (ArrayIndexOutOfBoundsException e) {
		//	throw new RuntimeException("Wrong internal storage for the data: stored "+content.length+" values for "+getParentsDimensionality()+" parents dim and "+domain.size()+ " values", e);
		//}
	}
	
	
	protected int[] _getParentIndices(Map<NodeCategorical,String> parent2Value) {
		
		if (parent2Value.size() != parents.size() || !parent2Value.keySet().containsAll(parents)) {
			throw new IllegalArgumentException("expecting all the parents values to be defined");
		}
		
		// find the indices of parents
		int[] parentIndices = new int[parents.size()];
		
		for (int i=0; i<parentIndices.length; i=i+1) {
		
			parentIndices[i] = parentsArray[i].getDomainIndex(parent2Value.get(parentsArray[i]));
		}
		
		return parentIndices;
	}
	
	/**
	 * For a list of parameters such as gender, male, age, 0-15, returns the indices of the values 
	 * for each index of parent. 
	 * 
	 * @param parentAndValue
	 * @return
	 */
	protected final int[] _getParentIndices(Object ... parentAndValue) {
		
		if (parentAndValue.length % 2 != 0)
			throw new IllegalArgumentException("expecting a list of parameters such as gender, male, age, 0-15");
		if (parentAndValue.length/2 != parents.size()) 
			throw new IllegalArgumentException("not enough parameters");
		
		// find the indices of parents
		int[] parentIndices = new int[parentAndValue.length/2];
		
		for (int i=0; i<parentAndValue.length; i=i+2) {
		
			// find parent based on our input
			Object parentRaw = parentAndValue[i];
			NodeCategorical parent = null;
			int idxParent = -1;
			
			if (parentRaw instanceof NodeCategorical) {
				parent = (NodeCategorical)parentRaw;
			} else if (parentRaw instanceof String) {
				// search for the parent by name 
				parent = getParent((String)parentRaw);
			} else {
				throw new IllegalArgumentException("unable to find parent "+parentRaw);
			}

			idxParent = parent2index.get(parent);
			
			// find attribute
			String value = (String)parentAndValue[i+1];
			int idxInDomain = parent.getDomainIndex(value);
			
			parentIndices[idxParent] = idxInDomain;
		}
		
		return parentIndices;
	}
	
	
	/**
	 * returns the total of every single probability
	 * @return
	 */
	public final double getSum() {
		double r = 0;
		for (double m: content) {
			r += m;
		}
		return r;
	}
	
	/**
	 * returns the dimensionality of all the parents; for instance if there is only one parent gender (male|female), then the dimensionality is 2; 
	 * if there is a second parent age3, then the dimensionality will be 6. 
	 * If there is no parent dimensionality is 1.
	 * @return
	 */
	public int getParentsDimensionality() {
		return parents.stream().mapToInt(NodeCategorical::getDomainSize).reduce(1, Math::multiplyExact); 
	}
	
	public void normalize() {
		
		//System.out.println("before:\t"+Arrays.toString(content));
		
		IteratorCategoricalVariables it = cNetwork.iterateDomains(getParents());
		while (it.hasNext()) {
			Map<NodeCategorical,String> n2s = it.next();
			
			double total = .0;
			for (String n: domain) {
				total += getProbability(n, n2s);
			}
			if (total == 0) {
				// no data at all ! we have to assume equiprobability
				System.out.println(
						"equiprobability for p("+name+"|"+
						n2s.entrySet().stream().map(k2v -> k2v.getKey().getName()+"="+k2v.getValue()).collect(Collectors.joining(","))+
						")");
				double eq = 1.0 / domain.size();
				for (String n: domain) {
					setProbabilities(eq, n, n2s);
				}
			} else if (Math.abs(total - 1.0) > 10e-8) {
				System.out.println(
						"normalizing p("+name+"|"+
						n2s.entrySet().stream().map(k2v -> k2v.getKey().getName()+"="+k2v.getValue()).collect(Collectors.joining(","))+
						")");
				for (String n: domain) {
					double p = getProbability(n, n2s);
					setProbabilities(p/total, n, n2s);
				}
			}
		}
		
		//System.out.println("after:\t"+Arrays.toString(content));
		
		if (!isValid()) {
			throw new RuntimeException("the node is not valid after normalization: "+
								this.collectInvalidityReasons());
		}
		
	}
	
	/**
	 * returns the probability conditional to all parents P(V=v | parents=*)
	 * @param d
	 * @return
	 */
	public double getConditionalProbability(String att) {
		
		// TODO reactivate logger.trace("computing conditional probability p({}={})", name, att);
		
		final int idxAtt = getDomainIndex(att);
		
		// [1,2,3,4]
		// [1,2]
		// [1,2,3]
		
		// cursor = 2
		// 1,1,1
		// 1,1,2,
		// 1,1,3
		
		// 1,2,1
		// 1,2,2
		// 1,2,3
		
		double res = 0.;
		for (int nb=0; nb<getParentsCardinality();nb++) {
			
			// shift next
			int[] idxParents = new int[parents.size()];

			// climb upwards until we find a place where we can increase the index
			int cursorParents = parents.size()-1;
			while (cursorParents > -1 && idxParents[cursorParents] >= parentsArray[cursorParents].getDomainSize()) {
				idxParents[cursorParents] = 0;
				cursorParents--;
			}
			
			if (cursorParents > -1)
				// then shift next
				idxParents[cursorParents]++;
				
			res += getProbability(idxAtt, idxParents);
			InferencePerformanceUtils.singleton.incAdditions();
			
		}
	
		// TODO reactivate logger.trace("computed conditional probability p({}={})={}", name, att, res);

		return res;
		
	}
	
	public double getConditionalProbabilityPosterior(
						String att, 
						Map<NodeCategorical,String> evidence, 
						Map<NodeCategorical,Map<String,Double>> alreadyComputed) {

		return getConditionalProbabilityPosterior(att, evidence, alreadyComputed, Collections.emptyMap());
	}
	

	/**
	 * returns P(V=v | parents) TODO terms and semantics
	 * @param d
	 * @return
	 */
	public double getConditionalProbabilityPosterior(
							String att, 
							Map<NodeCategorical,String> evidence, 
							Map<NodeCategorical,Map<String,Double>> alreadyComputed, 
							Map<NodeCategorical,String> forcedValue) {
		
		if (logger.isDebugEnabled()) {
			logger.debug("computing posteriors for p("+name+"="+att+"|"+evidence+")");
			logger.debug("alreadyComputed: " + alreadyComputed);
		}
		
		// quickest exist: maybe evidence says somehting about us, in this case we just return it !
		if (evidence.containsKey(this)) {
			
			if (evidence.get(this).equals(att)) {
				if (logger.isDebugEnabled())
					logger.debug("from evidence, posteriors p("+name+"="+att+")=1.0");
				return 1.;
			} else {
				if (logger.isDebugEnabled())
					logger.debug("from evidence, posteriors p("+name+"="+att+")=0.0");
				return 0.;
			}
		}
		
		// another quick exit: maybe that was already computed in the past, so why bother ? 
		if (alreadyComputed != null) {
			Map<String,Double> done = alreadyComputed.get(this);
			if (done != null) {
				Double res = done.get(att);
				if (res != null) {
					return res;
				}
			}
		}
		
		// quick exit: maybe we have no parent, in this case we just return the probability we store
		if (!hasParents()) {
			if (logger.isDebugEnabled())
				logger.debug("no parent, returning internal probability");
			return getConditionalProbability(att);
		}
		
		final int idxAtt = getDomainIndex(att);
		
		// [1,2,3,4]
		// [1,2]
		// [1,2,3]
		
		// cursor = 2
		// 1,1,1
		// 1,1,2,
		// 1,1,3
		
		// 1,2,1
		// 1,2,2
		// 1,2,3
		
		double resCond = 0.;
		//BigDecimal resNonCond = BigDecimal.ZERO;

		// list all the dimensions to be explored
		int[] idxParents = new int[parents.size()];
		int totalCard = 1;
		for (int p=0; p<parents.size(); p++) {
			if (forcedValue.containsKey(parentsArray[p])) {
				// if the value is locked, use it 
				idxParents[p] = parentsArray[p].getDomainIndex(forcedValue.get(parentsArray[p]));
			} else {
				idxParents[p] = parentsArray[p].getDomainSize()-1;
				totalCard *= parentsArray[p].getDomainSize();
			}
			
		}
		int cursorParents = parents.size()-1;
		
		for (int nb=0; nb<totalCard;nb++) {
			
			if (logger.isDebugEnabled()) {
			logger.debug("now cursor parents "+cursorParents+" idxParents " + idxParents );
			logger.debug("adding to probability p("+name+"="+att+"|*) from parents "+
					parents.stream().collect(Collectors.toMap(NodeCategorical::getName, p -> p.getValueIndexed(idxParents[Arrays.asList(parentsArray).indexOf(p)]))) 
					);
			}
			
			double pUsGivenSomething = getProbability(idxAtt, idxParents);
			double pSomething = 1.;
			for (NodeCategorical p: parents) {
				int idxPValue = idxParents[Arrays.asList(parentsArray).indexOf(p)]; // TODO inefficient
				String pAtt = p.getValueIndexed(idxPValue);
				
				if (logger.isDebugEnabled())		
					logger.debug("computing posteriors for parent p("+p.name+"="+pAtt+")");
				double cpp = p.getConditionalProbabilityPosterior(pAtt, evidence, alreadyComputed); 
				pSomething *= cpp;
				if (logger.isDebugEnabled())		
					logger.debug("cumulated * "+cpp+" = " + pSomething);

				if (pSomething == 0.) {
					// we can even break that loop: no multiplication will ever change that result !
					if (logger.isDebugEnabled())		
						logger.debug("reached p=0, stopping there");
					break;
				} 
			
			}
					
			//resNonCond = resNonCond.add(pUsGivenSomething);
			resCond += pUsGivenSomething * pSomething;
			if (logger.isDebugEnabled())		
				logger.debug("the probability p("+name+"="+att+"|*) is now after addition " + resCond);

			/*
			if (resCond.compareTo(BigDecimal.ONE) == 0) {
				// we can even break that loop: the probability will never become greater than 1!
				logger.trace("reached p=1, stopping there");
				break;
			} 
		 */
			// climb upwards until we find a place where we can increase the index
			if (logger.isDebugEnabled())		
				logger.debug("initial cursor parents "+cursorParents+" idxParents "+idxParents);
			
			
			/*
			 * idxParents [3 2]  cursor parents 1
			 * idxParents [2 2] cursor parents 1
			 * idxParents [1 2] cursor parents 1
			 * idxParents [0 2] cursor parents 1
			 * idxPraents [-1 2] cursor parents 1 !!!
			 * idxParents [3 1]
			 * [2 1]
			 * [1 1]
			 * [0 1]
			 * [-1 1] !!!
			 * [3 0] 
			 * [2 0]
			 * [1 0]
			 * [0 0]
			 * [-1 0] !!!
			 * [-1 -1] !!
			 */
			
			// shift to the lower value for the current parent domain
			//idxParents[cursorParents]--;
			for (int p=0; p<idxParents.length; p++) {
				
				// do not change if locked
				if (forcedValue.containsKey(parentsArray[p]))
					continue;

				idxParents[p] --;
				if (idxParents[p] < 0)
					idxParents[p] = parentsArray[p].getDomainSize()-1;
				else 
					break;
			}
			if (idxParents[0] < 0)
				break;
		}
	
		if (logger.isDebugEnabled())
			logger.debug("computed posteriors for p("+name+"="+att+"|"+evidence+")="+resCond);

		Map<String,Double> v2p = alreadyComputed.get(this);
		if (v2p == null) {
			v2p = new HashMap<>();
			try {
				alreadyComputed.put(this, v2p);
			} catch (UnsupportedOperationException e) {
				// ignore it; probably we were called with an empty map
			}
		}
		v2p.put(att, resCond);
		
		return resCond;
		
	}
	
	
	public double getConditionalProbabilityPosterior(String att) {
		
		return getConditionalProbabilityPosterior(
				att, 
				Collections.emptyMap(), 
				Collections.emptyMap(), 
				Collections.emptyMap()
				);
		
	}
	

	public double getPosterior(String key, Object ... parentAndValue) {
		return content[_getIndex(key, parentAndValue)];
	}
	
	/**
	 * returns true if the conditional probability sums to 1
	 * @return
	 */
	public boolean isValid() {
						
		// every single probability in our probas is non null and in [0:1]
		for (int i=0; i<content.length; i++) {
			if ( /*content[i] == null ||*/ (content[i] < 0) || (content[i] > 1)) 
				return false;
		}
		
		/*
		// each conditional probability in our domain is summing to 1 for each combination of parents values
		for (String v: domain) {
			double post = getConditionalProbability(v);
			if (post > 1.0)
				return false;
			sumConditionals += post;
		}*/
		
		
		// TODO check the conditionals sum to 1 conditionnaly !
		
		return (int)Math.round(getSum()) == getParentsDimensionality();
	}
	
	/**
	 * lists the problems identified during the validation, or null if no problem.
	 * @return
	 */
	public List<String> collectInvalidityReasons() {

		Set<String> res = new TreeSet<>();
		
		// every single probability in our probas is non null and in [0:1]
		for (int i=0; i<content.length; i++) {
			if ( /*content[i] == null ||*/ (content[i] < 0) || (content[i] > 1)) 
				res.add("there is a value not in [0:1]: "+content[i]);
		}
		
		// each conditional probability in our domain is summing to 1 for each combination of parents values
		/*double sumConditionals = .0;
		for (String v: domain) {
			double post = getConditionalProbability(v);
			if (post > 1.0)
				res.add("the conditional probability for "+v+" is "+post);
			sumConditionals += post;
		}*/
				
		double sum = getSum();
		if ((int)Math.round(sum) != getParentsCardinality()) {
			res.add("the sum is "+sum+" instead of "+getParentsDimensionality());
		}

		if (res.isEmpty())
			return null;
		
		return new LinkedList<>(res);
	}
	
	public void toXMLBIF(StringBuffer sb) {
		
		// define the variable
		sb.append("<VARIABLE TYPE=\"").append("nature").append("\">\n");
		sb.append("\t<NAME>").append(StringEscapeUtils.escapeXml10(getName())).append("</NAME>\n");
		for (String s: domain)
			sb.append("\t<OUTCOME>").append(StringEscapeUtils.escapeXml10(s)).append("</OUTCOME>\n");
		sb.append("</VARIABLE>\n");
		sb.append("\n");
		
		// define the distribution
		sb.append("<DEFINITION>\n");
		sb.append("\t<FOR>").append(StringEscapeUtils.escapeXml10(getName())).append("</FOR>\n");
		for (NodeCategorical n: parentsArray)
			sb.append("\t<GIVEN>").append(StringEscapeUtils.escapeXml10(n.getName())).append("</GIVEN>\n");

		sb.append("\t<TABLE>");
		for (Double p: content) {
			sb.append(p.toString()).append(" ");
		}
		sb.append("</TABLE>\n");

		sb.append("</DEFINITION>\n");
		
		
	}
	
	private static Pattern patternReplaceNonNumeric = Pattern.compile("[^a-zA-Z0-9]+");
	private static Pattern patternFirstCharNumeric = Pattern.compile("^[a-zA-Z].*");

	public static String convertDomainValueToNormalizedIdentifier(String domainValue) {
		
		String res = patternReplaceNonNumeric.matcher(domainValue).replaceAll("_");

		if (!patternFirstCharNumeric.matcher(res).matches())
			res = "z"+res;
		
		return res;
			
	}
	

	
	public void toBIF(StringBuffer sb) {
		
		// define the variable
		sb.append("variable ").append(convertDomainValueToNormalizedIdentifier(name)).append(" {\n");
		sb.append("   type discrete [ ").append(domain.size()).append(" ] { ").append(domain.stream().map(n -> convertDomainValueToNormalizedIdentifier(n)).collect(Collectors.joining(", "))).append(" };\n");
		sb.append("}\n");
		
		// define the distribution
		sb.append("probability ( ").append(convertDomainValueToNormalizedIdentifier(name));
		if (!parents.isEmpty()) {
			sb.append(" | ").append(parents.stream().map(n -> convertDomainValueToNormalizedIdentifier(n.getName())).collect(Collectors.joining(", ")));
		}
		sb.append(" ) {\n");
		
		if (parents.isEmpty()) {
			// if no parent: just a table in a line
			sb.append("   table ").append(Arrays.stream(content).mapToObj(d -> Double.toString(d)).collect(Collectors.joining(", "))).append(";\n");
		} else {
			// if parents: a tabular representation such as 
			// (ParentValue1, ParentValue2, ...) myVal1, myVal2...
			// (ParentValue1, ParentValue2, ...) myVal1, myVal2...
			IteratorCategoricalVariables it = cNetwork.iterateDomains(getParents());
			while (it.hasNext()) {
				Map<NodeCategorical,String> n2s = it.next();
				
				sb.append(" (").append(n2s.values().stream().map(v -> convertDomainValueToNormalizedIdentifier(v)).collect(Collectors.joining(", "))).append(") ");
				sb.append(domain.stream().map(n -> Double.toString(getProbability(n, n2s))).collect(Collectors.joining(", ")));
				sb.append(";\n");
				
			}	
		}
		
		sb.append("}\n");
		
	}

	public void toNet(StringBuffer sb) {
		
		// define the variable
		sb.append("node ").append(convertDomainValueToNormalizedIdentifier(name)).append("\n{\n");
		sb.append("  states = ( ").append(domain.stream().map(n -> '"'+convertDomainValueToNormalizedIdentifier(n)+'"').collect(Collectors.joining(" "))).append(" );\n");
		sb.append("}\n");
		
		// define the distribution
		List<NodeCategorical> parentsL = new ArrayList<>(getNetwork().enumerateNodes());
		parentsL.retainAll(parents);
		sb.append("potential ( ").append(convertDomainValueToNormalizedIdentifier(name));
		if (!parents.isEmpty()) {
			sb.append(" | ").append(parentsL.stream().map(n -> convertDomainValueToNormalizedIdentifier(n.getName())).collect(Collectors.joining(" ")));
		}
		sb.append(" )\n{\n");
		
		sb.append("  data = ( ");
		if (parents.isEmpty()) {
			// if no parent: just a table in a line
			sb.append(Arrays.stream(content).mapToObj(d -> Double.toString(d)).collect(Collectors.joining(" ")));
		} else {
			// if parents: 
			List<NodeCategorical> parentsLR = new ArrayList<>(parentsL);
			Collections.reverse(parentsLR);
			IteratorCategoricalVariables it = cNetwork.iterateDomains(parentsLR);
			Map<NodeCategorical,String> previous = null;
			// open all parenthesis
			for (NodeCategorical p: parentsL)
				sb.append("(");
			while (it.hasNext()) {
				Map<NodeCategorical,String> n2s = it.next();

				if (previous != null) {
					for (NodeCategorical p: parentsL) 
						if (!previous.get(p).equals(n2s.get(p)))
							sb.append(")");
					for (NodeCategorical p: parentsL) 
						if (!previous.get(p).equals(n2s.get(p)))
							sb.append("(");
				} 
				previous = n2s;

				sb.append(domain.stream().map(n -> Double.toString(getProbability(n, n2s))).collect(Collectors.joining(" ")));
			}	
			// close all parenthesis
			for (NodeCategorical p: parentsL)
				sb.append(")");
		}
		sb.append(" );\n");
		
		sb.append("}\n");
		
	}
	
	/**
	 * Gets this variable as a factor
	 * @return
	 */
	public Factor asFactor() {
		
		Set<NodeCategorical> variables = new HashSet<>(parents);
		variables.add(this);
		
		Factor f = new Factor(cNetwork, variables);
		
		for (IteratorCategoricalVariables it = cNetwork.iterateDomains(this.parents); it.hasNext(); ) {
			Map<NodeCategorical,String> v2n = it.next();
			for (String v: this.domain) {
				double d = this.getProbability(v, v2n);
				HashMap<NodeCategorical,String> v2n2 = new HashMap<>(v2n);
				v2n2.put(this, v);
				f.setFactor(v2n2, d);
			}
		}
		return f;
	}
	
	/**
	 * A node together its parents set is defined as a family
	 * @return
	 */
	public Set<NodeCategorical> family() {
		HashSet<NodeCategorical> res = new HashSet<>(this.getParents());
		res.add(this);
		return res;
	}
	
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("p( ");
		sb.append(getName());
		if (hasParents())
			sb.append(" | ");
		boolean first = true;
		for (NodeCategorical p: getParents()) {
			//sb.append(p.getName());
			if (first) 
				first = false;
			else
				sb.append(", ");
			sb.append(p.getName());

		}
		sb.append(" )");
		return sb.toString();
	}
	
	/**
	 * Writes the entire representation of the CPT 
	 * @return
	 */
	public String toStringComplete() {
		
		StringBuffer sb = new StringBuffer();
		
		toStringComplete(sb);
		
		return sb.toString();
	}
	
	public void toStringComplete(StringBuffer sb) {
				
		for (String v: getDomain()) {
			
			IteratorCategoricalVariables it = cNetwork.iterateDomains(getParents());
			while (it.hasNext()) {
				Map<NodeCategorical,String> n2s = it.next();
				sb.append("p( ").append(getName()).append("=").append(v);
				if (hasParents()) {
					sb.append(" | ");
					sb.append(n2s.entrySet().stream().map(e -> e.getKey().name+"="+e.getValue()).collect(Collectors.joining(", ")));
				}
				sb.append(" ) = ").append(getProbability(v, n2s)).append("\n");
			}
				
				
		}
	
	}

	
}
