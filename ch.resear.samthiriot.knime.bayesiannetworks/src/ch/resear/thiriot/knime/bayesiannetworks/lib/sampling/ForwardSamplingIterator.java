package ch.resear.thiriot.knime.bayesiannetworks.lib.sampling;

import java.util.Iterator;
import java.util.Map;

import cern.jet.random.engine.RandomEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.AbstractInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.SimpleConditionningInferenceEngine;

// TODO make it splititerator ?

public class ForwardSamplingIterator implements Iterator<EntitiesAndCount> {
	
	public static final String GENERATION_METHOD_NAME = "forward sampling";

	private final RandomEngine random;
	private final CategoricalBayesianNetwork bn;
	private final int count;
	
	private final AbstractInferenceEngine engine;
	
	private int done;
	
	public ForwardSamplingIterator(
			RandomEngine random, 
			CategoricalBayesianNetwork bn,
			int count,
			ILogger logger
			) {
		
		this.random = random;
		this.bn = bn;
		this.count = count;
		
		this.engine = new SimpleConditionningInferenceEngine(logger, random, bn);
		
		done = 0;
	}

	@Override
	public boolean hasNext() {
		return done < count;
	}

	@Override
	public EntitiesAndCount next() {
		
    	Map<NodeCategorical,String> variable2value = engine.sampleOne();
    	done++;
    	
    	return new EntitiesAndCount(variable2value, 1);
    	
	}

}
