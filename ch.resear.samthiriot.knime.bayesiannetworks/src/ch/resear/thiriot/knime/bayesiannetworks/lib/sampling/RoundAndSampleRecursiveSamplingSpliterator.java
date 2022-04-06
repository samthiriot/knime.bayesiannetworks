package ch.resear.thiriot.knime.bayesiannetworks.lib.sampling;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.knime.core.node.ExecutionMonitor;

import cern.jet.random.engine.RandomEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.AbstractInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.SimpleConditionningInferenceEngine;

public final class RoundAndSampleRecursiveSamplingSpliterator 
				extends RecursiveSamplingSpliterator<RandomEngine> {

	public static final String GENERATION_METHOD_NAME = "round then random";

	public RoundAndSampleRecursiveSamplingSpliterator(
			int count, 
			CategoricalBayesianNetwork bn, 
			RandomEngine rng,
			AbstractInferenceEngine engine, 
			ExecutionMonitor exec, 
			ILogger ilogger) {
		
		this(count, bn.enumerateNodes(), rng, engine, exec, ilogger);
	}

	protected RoundAndSampleRecursiveSamplingSpliterator(
			int count, 
			List<NodeCategorical> nodes, 
			RandomEngine rng,
			AbstractInferenceEngine engine, 
			ExecutionMonitor exec, 
			ILogger ilogger) {
		
		this(
				count, 
				nodes.get(0), 
				nodes.subList(1, nodes.size()),
				Collections.emptyMap(), 
				Collections.emptyMap(),
				rng,
				engine,
				exec,
				ilogger,
				"",
				nodes.get(0).getDomain()
				);
	}

	protected RoundAndSampleRecursiveSamplingSpliterator(
			int count, 
			NodeCategorical n, 
			List<NodeCategorical> remaining,
			Map<NodeCategorical, String> evidence, 
			Map<NodeCategorical, Map<String, Double>> alreadyComputed,
			RandomEngine rng, 
			AbstractInferenceEngine engine, 
			ExecutionMonitor exec, 
			ILogger ilogger, 
			String name,
			List<String> domain) {
		
		super(count, n, remaining, evidence, alreadyComputed, rng, engine, exec, ilogger, name, domain);
	}
	
	protected RoundAndSampleRecursiveSamplingSpliterator(
			int count, 
			NodeCategorical n, 
			List<NodeCategorical> remaining,
			Map<NodeCategorical, String> evidence, 
			Map<NodeCategorical, Map<String, Double>> alreadyComputed,
			RandomEngine rng, 
			AbstractInferenceEngine engine, 
			ExecutionMonitor exec, 
			ILogger ilogger, 
			String name,
			List<String> domain,
			Map<String,Integer> value2count
			) {
		
		super(count, n, remaining, evidence, alreadyComputed, rng, engine, exec, ilogger, name, domain,
				value2count);
	}

	@Override
	protected RecursiveSamplingSpliterator<RandomEngine> createSubIterator(
			int count, 
			NodeCategorical n, 
			List<NodeCategorical> remaining,
			Map<NodeCategorical, String> evidence, 
			Map<NodeCategorical, Map<String, Double>> alreadyComputed,
			List<String> domain
			) {
		
		return new RoundAndSampleRecursiveSamplingSpliterator(
				count, n, remaining, evidence, alreadyComputed, 
				rng, engine, exec, logger, name, domain);
	}
	

	@Override
	protected RecursiveSamplingSpliterator<RandomEngine> createSubIterator(int count, NodeCategorical n,
			List<NodeCategorical> remaining, Map<NodeCategorical, String> evidence,
			Map<NodeCategorical, Map<String, Double>> alreadyComputed, List<String> otherDomain,
			Map<String, Integer> value2count) {
		return new RoundAndSampleRecursiveSamplingSpliterator(
				count, n, remaining, evidence, alreadyComputed, 
				rng, 
				new SimpleConditionningInferenceEngine(logger, null, engine.getBN()), 
				exec, logger, name, otherDomain,
				value2count);
	}
		

	@Override
	protected int[] getCounts(int count, double[] probabilities) {

		// how many entities should be generated here?
		double[] frequencies = new double[probabilities.length];
		int[] counts = new int[probabilities.length];
		int totalgenerated = 0;
		for (int i=0; i<frequencies.length; i++) {
			frequencies[i] = probabilities[i] * count;
			final int floored = (int)frequencies[i];
			counts[i] = floored; // equivalent to Math.floor
			totalgenerated += floored; 
		}
		if (debug) {
			logger.debug("\tfrequencies:");
			logger.debug("\t"+java.util.Arrays.toString(frequencies));
			logger.debug("\tcounts:");
			logger.debug("\t"+java.util.Arrays.toString(counts));
		}
		// maybe we did not yet generated everything?
		if (totalgenerated < count) {
			
			if (debug)
				logger.debug("\twe still have to distribute "+(count-totalgenerated));
			
			// what is the remainder?
			//double[] missing = counts.clone();
			double[] missingCumulated = new double[frequencies.length];
			double missingSum = 0.0;
			for (int i=0; i<frequencies.length; i++) {
				final double diff = frequencies[i] - counts[i];
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
				
				//if (debug)
				//	logger.debug("\tweighted roulette for p="+random //+" on "java.util.Arrays.toString(missingCumulated)
				//				);
				
				// TODO use alias method instead?
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
				
		if (debug) {
			logger.warn("\tcounts:");
			logger.warn("\t"+java.util.Arrays.toString(counts));
		}
		//System.out.println("counts:\t "+java.util.Arrays.toString(countsf));
	
		return counts;
	}



}
