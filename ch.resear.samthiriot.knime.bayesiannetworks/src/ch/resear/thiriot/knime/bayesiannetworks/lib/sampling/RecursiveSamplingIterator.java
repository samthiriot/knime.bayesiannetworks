package ch.resear.thiriot.knime.bayesiannetworks.lib.sampling;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;

import cern.jet.random.engine.RandomEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.AbstractInferenceEngine;

public class RecursiveSamplingIterator implements Iterator<EntitiesAndCount> {

	private final boolean isRoot;
	private final String name;
	
	private final RandomEngine rng;
	
	private final ExecutionMonitor exec;
	private final ILogger logger;
	private final boolean debug;
	
	private final NodeCategorical node;
	
	private final Iterator<Entry<String,Integer>> itDomainAndCount;
	private RecursiveSamplingIterator itSub = null;
	
	private final Map<NodeCategorical,String> evidence;
	private final List<NodeCategorical> remaining;
	
	private final Map<NodeCategorical,Map<String,Double>> alreadyComputedNow;
	
	private final AbstractInferenceEngine engine;
	
	public RecursiveSamplingIterator(
			int count, 
			CategoricalBayesianNetwork bn, 
			RandomEngine rng,
			AbstractInferenceEngine engine,
			ExecutionMonitor exec,
			ILogger ilogger) {
		this(count, bn.enumerateNodes(), rng, engine, exec, ilogger);
	}

	public RecursiveSamplingIterator(
			int count, 
			List<NodeCategorical> nodes, 
			RandomEngine rng,
			AbstractInferenceEngine engine,
			ExecutionMonitor exec,
			ILogger ilogger) {
		
		this(count, nodes.get(0), nodes.subList(1, nodes.size()),
			Collections.emptyMap(), Collections.emptyMap(),
			rng,
			engine,
			exec,
			ilogger,
			""
			);		
		
	}

	public RecursiveSamplingIterator(
			int count, NodeCategorical n, List<NodeCategorical> remaining,
			Map<NodeCategorical,String> evidence,
			Map<NodeCategorical,Map<String,Double>> alreadyComputed,
			RandomEngine rng,
			AbstractInferenceEngine engine,
			ExecutionMonitor exec,
			ILogger ilogger,
			String name
			) {
		
		this.isRoot = evidence.isEmpty();
		this.exec = exec;
		this.node = n;
		this.name = name + " -- " + node.name;
		this.rng = rng;
		this.evidence = evidence;
		this.remaining = remaining;
		this.logger = ilogger;
		this.engine = engine;
		this.debug = ilogger.isDebugEnabled();
		
		// build the table of probabilities for this case
		if (debug) {
			logger.debug("iterator "+this.name+(remaining.isEmpty()?" -| ":"")+" (generate "+count+")"); 
			logger.debug("\tcomputing p("+n.name+"|"+evidence.entrySet().stream().map(e -> e.getKey().name+"="+e.getValue()).collect(Collectors.joining(","))+")");
		}
		engine.clearEvidence();
		engine.addEvidence(evidence);
		double[] probabilities = node.getDomain()
									.stream()
									.mapToDouble(
											s -> engine.getConditionalProbability(node, s) // n.getConditionalProbabilityPosterior(s, evidence, alreadyComputed)
									).toArray();
		if (debug) {
			logger.debug("\tprobabilities: "+java.util.Arrays.toString(probabilities));
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
		
		// how many entities should be generated here?
		double[] frequencies = probabilities.clone();
		for (int i=0; i<frequencies.length; i++) {
			frequencies[i] = frequencies[i] * count;
		}
		if (debug) {
			logger.debug("\tfrequencies:");
			logger.debug("\t"+java.util.Arrays.toString(frequencies));
		}
		//System.out.println("counts:\t "+java.util.Arrays.toString(counts));

		// how many fixed entities should be generated?
		int[] counts = new int[frequencies.length];
		int totalgenerated = 0;
		for (int i=0; i<frequencies.length; i++) {
			counts[i] = (int)Math.floor(frequencies[i]);
			totalgenerated += counts[i]; 
		}
		//System.out.println("numbers:\t "+java.util.Arrays.toString(countsf));
		if (debug) {
			logger.debug("\tcounts:");
			logger.debug("\t"+java.util.Arrays.toString(counts));
		}
		// maybe we did not yet generated everything?
		if (totalgenerated < count) {
			
			if (debug)
				logger.debug("\twe still have to distribute "+(count-totalgenerated));
			
			// what is the remainder?
			//double[] missing = counts.clone();
			double[] missingCumulated = frequencies.clone();
			double missingSum = 0.0;
			for (int i=0; i<frequencies.length; i++) {
				double diff = frequencies[i] - counts[i];
				//missing[i] = diff;
				missingCumulated[i] = diff + missingSum;
				missingSum += diff;
			}
			//System.out.println("remains:\t "+java.util.Arrays.toString(missing));

			// now we have to split the remaining entities!
			//System.out.println("remaining "+(count-totalgenerated)+" entities for "+java.util.Arrays.toString(missing));
			
			// select one entity!
			// generate a random number between [0 : missingCumulated]
			while (totalgenerated < count) {
				
				final double random = rng.nextDouble() * missingSum;
				
				if (debug)
					logger.debug("\tweighted roulette for p="+random //+" on "java.util.Arrays.toString(missingCumulated)
							);
				
				int idx = java.util.Arrays.binarySearch(missingCumulated, random);
				
				if (idx > 0) {
					counts[idx]++;
				} else {
					counts[-idx-1]++;
				}
				totalgenerated++;
				
			}
			
			if (debug) {
				logger.debug("\tcounts:");
				logger.debug("\t"+java.util.Arrays.toString(counts));
			}

		}
		
		//System.out.println("counts:\t "+java.util.Arrays.toString(countsf));
	
		Map<String,Integer> value2count = new HashMap<>();
		for (int i=0; i<frequencies.length; i++) {
			if (counts[i] == 0)
				continue;
			value2count.put(n.getDomain(i), counts[i]);
		}
	
		itDomainAndCount = value2count.entrySet().iterator();
	}
	
	@Override
	public boolean hasNext() {
		try {
			exec.checkCanceled();
		} catch (CanceledExecutionException e) {
			return false;
		}
		if (itSub != null && itSub.hasNext())
			return true;
		else  {
			itSub = null;
			return itDomainAndCount.hasNext();
		}
	}

	@Override
	public EntitiesAndCount next() {

		if (itSub != null)
			return itSub.next();
		
		Entry<String,Integer> v2c = itDomainAndCount.next();
		String v = v2c.getKey();
		Integer countNow = v2c.getValue();
		
		// explore the combinations to generate!
		Map<NodeCategorical,String> evidenceNow = new HashMap<>(evidence);
		evidenceNow.put(node,v);
		
		// maybe we are done?
		if (remaining.isEmpty()) {
			// we have no more node to explore :-)
			// we are done
			return new EntitiesAndCount(evidenceNow, countNow);
		} else {
			// we have to continue recursively!
			itSub = new RecursiveSamplingIterator(
					countNow, 
					remaining.get(0), remaining.subList(1, remaining.size()),
					evidenceNow,
					alreadyComputedNow,
					rng,
					engine,
					exec,
					logger,
					name
					);
		
			return itSub.next();
		}
		
	}

	
	
}
