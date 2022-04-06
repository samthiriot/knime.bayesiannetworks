package ch.resear.thiriot.knime.bayesiannetworks.lib.sampling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;

import cern.colt.function.DoubleFunction;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.AbstractInferenceEngine;

/**
 * Samples from a Bayesian network recursively, by exploring first the root variable, 
 * then defining somehow how many entities to generate for each value of the domain,
 * then recursively defining the counts for the other variables.
 * 
 * @author Samuel Thiriot
 * 
 * @param <R>
 */
public abstract class RecursiveSamplingSpliterator<R extends DoubleFunction> 
					implements Spliterator<EntitiesAndCount> {

	protected final String name;
	
	protected final R rng;
	
	protected ExecutionMonitor exec;
	protected final ILogger logger;
	protected final boolean debug;
	
	private final NodeCategorical node;
	
	/**
	 * contains, for every domain value, the corresponding count estimated.
	 */
	private final Map<String,Integer> value2count;
	
	/**
	 * Contains the list of values of the domain to explore in this iterator
	 */
	private List<String> domainToExplore;
	private RecursiveSamplingSpliterator<R> itSub = null;
	
	private final Map<NodeCategorical,String> evidence;
	private final List<NodeCategorical> remaining;
	
	private final Map<NodeCategorical,Map<String,Double>> alreadyComputedNow;
	
	protected final AbstractInferenceEngine engine;
	
	private int count;
	
	public RecursiveSamplingSpliterator(
			int count, 
			CategoricalBayesianNetwork bn, 
			R rng,
			AbstractInferenceEngine engine,
			ExecutionMonitor exec,
			ILogger ilogger) {
		
		this(count, bn.enumerateNodes(), rng, engine, exec, ilogger);
	}

	protected RecursiveSamplingSpliterator(
			int count, 
			List<NodeCategorical> nodes, 
			R rng,
			AbstractInferenceEngine engine,
			ExecutionMonitor exec,
			ILogger ilogger) {
		
		this(count, nodes.get(0), nodes.subList(1, nodes.size()),
			Collections.emptyMap(), Collections.emptyMap(),
			rng,
			engine,
			exec,
			ilogger,
			"",
			nodes.get(0).getDomain()
			);		
		
	}
	
	protected abstract int[] getCounts(int count, double[] probabilities);

	protected RecursiveSamplingSpliterator(
			int count, NodeCategorical n, List<NodeCategorical> remaining,
			Map<NodeCategorical,String> evidence,
			Map<NodeCategorical,Map<String,Double>> alreadyComputed,
			R rng,
			AbstractInferenceEngine engine,
			ExecutionMonitor exec,
			ILogger ilogger,
			String name,
			List<String> domainToExplore
			) {
		
		this.exec = exec;
		this.node = n;
		this.name = name + " -- " + node.name;
		this.rng = rng;
		this.evidence = evidence;
		this.remaining = remaining;
		this.logger = ilogger;
		this.engine = engine;
		this.debug = ilogger.isDebugEnabled();
		this.count =  count;
		this.domainToExplore = new ArrayList<>(domainToExplore);
		
		if (count < 0)
			throw new IllegalArgumentException("count should not be negative");
		
		// build the table of probabilities for this case
		if (debug) {
			logger.debug("iterator "+this.name+(remaining.isEmpty()?" -| ":"")+" (generate "+count+")"); 
			logger.debug("\tcomputing p("+n.name+"|"+evidence.entrySet().stream().map(e -> e.getKey().name+"="+e.getValue()).collect(Collectors.joining(","))+")");
		}
		engine.clearEvidence();
		engine.addEvidence(evidence);
		double[] probabilities; 
		try {
			probabilities = node.getDomain()
										.stream()
										.mapToDouble(
												s -> engine.getConditionalProbability(node, s) // n.getConditionalProbabilityPosterior(s, evidence, alreadyComputed)
										).toArray();
			if (debug) {
				logger.debug("\tprobabilities: "+java.util.Arrays.toString(probabilities));
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
			throw new RuntimeException(
					"error when computing conditional probabilities for variable "+node+": "+e.getMessage()
					, e);
		}
		if (Double.isNaN(probabilities[0])) {
			throw new RuntimeException("unable to compute p("+n.name+"|"+evidence.entrySet().stream().map(e -> e.getKey().name+"="+e.getValue()).collect(Collectors.joining(","))+")");
		}
		//System.out.println("probabilities:\t "+java.util.Arrays.toString(probabilities));
		
		// add out values to what was already computed
		alreadyComputedNow = new HashMap<>(alreadyComputed);
		Map<String,Double> node2v = new HashMap<>();
		for (int i=0; i<probabilities.length; i++) {
			node2v.put(n.getDomain(i), probabilities[i]);
		}
		alreadyComputedNow.put(n, node2v);
		
		int[] counts;
		try {
			counts = getCounts(count, probabilities);
		} catch (RuntimeException e) {
			e.printStackTrace();
			throw new RuntimeException(
					"error computing the counts to generate for variable "+node+": "+e.getMessage(), 
					e);
		}
		
		/*
		System.out.println(name);
		System.out.println("\ttake "+count);
		System.out.println("\tprobas "+Arrays.toString(probabilities));
		System.out.println("\tcounts "+Arrays.toString(counts));
		*/
		
		if (debug) {
			logger.warn("\tcounts:");
			logger.warn("\t"+java.util.Arrays.toString(counts));
		}
		//System.out.println("counts:\t "+java.util.Arrays.toString(countsf));
	
		value2count = new HashMap<>();
		for (int i=0; i<probabilities.length; i++) {
			if (counts[i] == 0)
				continue;
			value2count.put(n.getDomain(i), counts[i]);
		}
		
	}
	
	protected RecursiveSamplingSpliterator(
			int count, NodeCategorical n, List<NodeCategorical> remaining,
			Map<NodeCategorical,String> evidence,
			Map<NodeCategorical,Map<String,Double>> alreadyComputed,
			R rng,
			AbstractInferenceEngine engine,
			ExecutionMonitor exec,
			ILogger ilogger,
			String name,
			List<String> domainToExplore,
			Map<String,Integer> value2count
			) {
		
		this.exec = exec;
		this.node = n;
		this.name = name + " -- " + node.name;
		this.rng = rng;
		this.evidence = evidence;
		this.remaining = remaining;
		this.logger = ilogger;
		this.engine = engine;
		this.debug = true; //ilogger.isDebugEnabled();
		this.count =  count;
		this.domainToExplore = new ArrayList<>(domainToExplore);
		
		// add out values to what was already computed
		this.alreadyComputedNow = alreadyComputed;
		
		this.value2count = value2count;
		
	}

	@Override
	public boolean tryAdvance(Consumer<? super EntitiesAndCount> action) {
		
		try {
			exec.checkCanceled();
		} catch (CanceledExecutionException e) {
			return false;
		}
		
		// find next
		try {
			
			if (itSub != null) {
				// we have a sub. Let's just delegate the processing to it
				boolean subAnswer = itSub.tryAdvance(action);
				if (subAnswer)
					// the sub can still produce results
					// no doubt, we will continue producing results!
					return true;
				else 
					// end of this sub
					itSub = null;
			} 
		
			// we have no sub. 
			if (domainToExplore.isEmpty())
				// if we reached the end of our domain, stop there
				return false;
			
			String v = null;
			Integer countNow = null;
			while (countNow == null && !domainToExplore.isEmpty()) {
				v = domainToExplore.remove(0);
				countNow = value2count.get(v);
			}
			if (countNow == null && domainToExplore.isEmpty()) {
				return false;
			}
			
			// explore the combinations to generate!
			Map<NodeCategorical,String> evidenceNow = new HashMap<>(evidence);
			evidenceNow.put(node,v);
			
			// maybe we are done?
			if (remaining.isEmpty()) {
				// we have no more node to explore :-)
				// we are done
				if (countNow != null) {
					EntitiesAndCount res = new EntitiesAndCount(evidenceNow, countNow);
					//System.out.println(res);
					action.accept(res);
				}
			} else {
				// we have to continue recursively!
				itSub = createSubIterator(countNow, 
						remaining.get(0), remaining.subList(1, remaining.size()),
						evidenceNow,
						alreadyComputedNow,
						remaining.get(0).getDomain()
						);
						
				boolean subAnswer = itSub.tryAdvance(action);
				if (subAnswer)
					// the sub can continue to produce
					return true;
				else 
					// end of this sub
					itSub = null;
			}		
		
			// we can continue... if there is more to explore!
			return !domainToExplore.isEmpty();
			
		} catch (RuntimeException e) {
			throw e;
		}
		
	}

	@Override
	public RecursiveSamplingSpliterator<R> trySplit() {

		// there is only one combination to explore here.
		if (domainToExplore.size() < 2) {
			// cannot split this one. 
			// but maybe the subiterator can be split?
			// TODO 
			return null;
		}
			
		// let's split the remaining domain
		int idxSplit = domainToExplore.size() / 2;
		List<String> otherDomain = domainToExplore.subList(idxSplit, domainToExplore.size());
		
		// adapt this domain
		domainToExplore = domainToExplore.subList(0, idxSplit);

		if (logger.isDebugEnabled())
			logger.debug("splitting "+node.name+" between "+domainToExplore+" and "+otherDomain);
		
		this.count = domainToExplore.stream().mapToInt(s -> value2count.get(s)).sum();
		//this.exec = exec;
		
		return createSubIterator(
				otherDomain.stream().mapToInt(s -> value2count.get(s)).sum(),
				node,
				remaining,
				evidence,
				alreadyComputedNow, 
				otherDomain,
				value2count);
	}

	
	
	protected abstract RecursiveSamplingSpliterator<R> createSubIterator(
			int count, NodeCategorical n, List<NodeCategorical> remaining,
			Map<NodeCategorical,String> evidence,
			Map<NodeCategorical,Map<String,Double>> alreadyComputed,
			List<String> otherDomain
			);
	
	protected abstract RecursiveSamplingSpliterator<R> createSubIterator(
			int count, NodeCategorical n, List<NodeCategorical> remaining,
			Map<NodeCategorical,String> evidence,
			Map<NodeCategorical,Map<String,Double>> alreadyComputed,
			List<String> otherDomain,
			Map<String,Integer> value2count
			);

	@Override
	public long estimateSize() {
		
		// Estimation: we have to compute combinations of domains of this node and subnodes
		
		long combinations = domainToExplore.size();
		for (NodeCategorical n: remaining) {
			combinations *= n.getDomainSize();
		}
		
		return combinations; 
	}

	@Override
	public int characteristics() {
		return Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.ORDERED;
	}

	
	
}
