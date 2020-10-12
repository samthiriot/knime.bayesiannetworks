package ch.resear.thiriot.knime.bayesiannetworks.lib.sampling;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.knime.core.node.ExecutionMonitor;

import cern.jet.random.Binomial;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.AbstractInferenceEngine;

public final class MultinomialRecursiveSamplingIterator extends RecursiveSamplingIterator<Binomial> {

	public static final String GENERATION_METHOD_NAME = "multinomial sampling";

	public MultinomialRecursiveSamplingIterator(int count, CategoricalBayesianNetwork bn, Binomial rng,
			AbstractInferenceEngine engine, ExecutionMonitor exec, ILogger ilogger) {
		this(count, bn.enumerateNodes(), rng, engine, exec, ilogger);
	}

	protected MultinomialRecursiveSamplingIterator(int count, List<NodeCategorical> nodes, Binomial rng,
			AbstractInferenceEngine engine, ExecutionMonitor exec, ILogger ilogger) {
		this(count, nodes.get(0), nodes.subList(1, nodes.size()),
				Collections.emptyMap(), Collections.emptyMap(),
				rng,
				engine,
				exec,
				ilogger,
				""
				);
	}

	protected MultinomialRecursiveSamplingIterator(int count, NodeCategorical n, List<NodeCategorical> remaining,
			Map<NodeCategorical, String> evidence, Map<NodeCategorical, Map<String, Double>> alreadyComputed,
			Binomial rng, AbstractInferenceEngine engine, ExecutionMonitor exec, ILogger ilogger, String name) {
		super(count, n, remaining, evidence, alreadyComputed, rng, engine, exec, ilogger, name);
	}
	

	@Override
	protected RecursiveSamplingIterator<Binomial> createSubIterator(int count, NodeCategorical n, List<NodeCategorical> remaining,
			Map<NodeCategorical, String> evidence, Map<NodeCategorical, Map<String, Double>> alreadyComputed) {
		return new MultinomialRecursiveSamplingIterator(count, n, remaining, evidence, alreadyComputed, rng, engine, exec, logger, name);
	}
		

	@Override
	protected int[] getCounts(int count, double[] probabilities) {

		// @see https://github.com/SurajGupta/r-source/blob/master/src/nmath/rmultinom.c
		
		int[] counts = new int[probabilities.length];
		
		//int totalgenerated = 0;
		int remainingCount = count;
		double remainingP = 1.0;
		for (int i=0; i<probabilities.length-1; i++) {
			if (remainingCount <= 0 || probabilities[i] == 0.0 || remainingP == 0) {
				counts[i] = 0;
			} else if (probabilities[i] == 1.0) {
				counts[i] = count;
				remainingP = 0.0;
				remainingCount = 0;
			} else {
				double pp = probabilities[i]/remainingP;
				if (pp >= 1.0) {
					counts[i] = remainingCount;
					remainingP = 0.0;
					remainingCount = 0;
				} else {
					//logger.warn("\tnext binom: take "+remainingCount+" with proba "+probabilities[i]+"/"+remainingP+"="+(probabilities[i]/remainingP));
					counts[i] = rng.nextInt(remainingCount, probabilities[i]/remainingP);
					remainingCount -= counts[i];
					remainingP -= probabilities[i];
				}
			}
		}
		counts[probabilities.length-1] = remainingCount;
		
		return counts;
	}


}
