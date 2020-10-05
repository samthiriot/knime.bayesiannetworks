package ch.resear.thiriot.knime.bayesiannetworks.lib.sampling;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;

import cern.colt.Arrays;
import cern.jet.random.AbstractContinousDistribution;
import cern.jet.random.engine.RandomEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.BayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;

public class RecursiveSamplingIterator implements Iterator<EntitiesAndCount> {

	private final boolean isRoot;
	private final String name;
	
	private final RandomEngine rng;
	
	private final ExecutionMonitor exec;
	
	private final NodeCategorical node;
	
	private final Iterator<Entry<String,Integer>> itDomainAndCount;
	private RecursiveSamplingIterator itSub = null;
	
	private final Map<NodeCategorical,String> evidence;
	private final List<NodeCategorical> remaining;
	
	private final Map<NodeCategorical,Map<String,Double>> alreadyComputedNow;
	
	public RecursiveSamplingIterator(
			int count, 
			CategoricalBayesianNetwork bn, 
			RandomEngine rng,
			ExecutionMonitor exec) {
		this(count, bn.enumerateNodes(), rng, exec);
	}

	public RecursiveSamplingIterator(
			int count, 
			List<NodeCategorical> nodes, 
			RandomEngine rng,
			ExecutionMonitor exec) {
		
		this(count, nodes.get(0), nodes.subList(1, nodes.size()),
			Collections.emptyMap(), Collections.emptyMap(),
			rng,
			exec,
			""
			);		
		
	}

	public RecursiveSamplingIterator(
			int count, NodeCategorical n, List<NodeCategorical> remaining,
			Map<NodeCategorical,String> evidence,
			Map<NodeCategorical,Map<String,Double>> alreadyComputed,
			RandomEngine rng,
			ExecutionMonitor exec,
			String name
			) {
		
		this.isRoot = evidence.isEmpty();
		this.exec = exec;
		this.node = n;
		this.name = name + " -- " + node.name;
		this.rng = rng;
		this.evidence = evidence;
		this.remaining = remaining;
		
		// build the table of probabilities for this case
		double[] probabilities = n.getDomain()
									.stream()
									.mapToDouble(
											s -> n.getConditionalProbabilityPosterior(s, evidence, alreadyComputed)
									).toArray();
		//System.out.println("probabilities:\t "+Arrays.toString(probabilities));
		
		// add out values to what was already computed
		alreadyComputedNow = new HashMap<>(alreadyComputed);
		Map<String,Double> node2v = new HashMap<>();
		for (int i=0; i<probabilities.length; i++) {
			node2v.put(n.getDomain(i), probabilities[i]);
		}
		alreadyComputedNow.put(n, node2v);
		
		// how many entities should be generated here?
		double[] counts = probabilities.clone();
		for (int i=0; i<counts.length; i++) {
			counts[i] = counts[i] * count;
		}
		//System.out.println("counts:\t "+Arrays.toString(counts));

		// how many fixed entities should be generated?
		int[] countsf = new int[counts.length];
		int totalgenerated = 0;
		for (int i=0; i<counts.length; i++) {
			countsf[i] = (int)Math.floor(counts[i]);
			totalgenerated += countsf[i]; 
		}
		//System.out.println("numbers:\t "+Arrays.toString(countsf));
		
		// maybe we did not yet generated everything?
		if (totalgenerated < count) {
			// what is the remainder?
			//double[] missing = counts.clone();
			double[] missingCumulated = counts.clone();
			double missingSum = 0.0;
			for (int i=0; i<counts.length; i++) {
				double diff = counts[i] - countsf[i];
				//missing[i] = diff;
				missingCumulated[i] = diff + missingSum;
				missingSum += diff;
			}
			//System.out.println("remains:\t "+Arrays.toString(missing));

			// now we have to split the remaining entities!
			//System.out.println("remaining "+(count-totalgenerated)+" entities for "+Arrays.toString(missing));
			
			// select one entity!
			// generate a random number between [0 : missingCumulated]
			while (totalgenerated < count) {
				final double random = rng.nextDouble() * missingSum;
				
				int idx = java.util.Arrays.binarySearch(missingCumulated, random);
				
				if (idx > 0) {
					countsf[idx]++;
				} else {
					countsf[-idx-1]++;
				}
				totalgenerated++;
				
			}
			

		}
		
		//System.out.println("counts:\t "+Arrays.toString(countsf));

		Map<String,Integer> value2count = new HashMap<>();
		for (int i=0; i<counts.length; i++) {
			if (countsf[i] == 0)
				continue;
			value2count.put(n.getDomain(i), countsf[i]);
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
					exec,
					name
					);
		
			return itSub.next();
		}
		
	}

	
	
}
