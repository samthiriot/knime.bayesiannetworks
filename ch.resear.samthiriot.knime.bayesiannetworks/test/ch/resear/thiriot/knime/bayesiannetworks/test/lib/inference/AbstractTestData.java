package ch.resear.thiriot.knime.bayesiannetworks.test.lib.inference;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractTestData {

	public final String name;
	public final String filename; 
	public final int precision;
	
	/**
	 * Stores the expected results for a given evidence
	 */
	public final Map<Map<String,String>,Map<String,Map<String,Double>>> evidence2expectedResults = new HashMap<>();
	
	public final Map<String,Map<String,Double>> variables2expectedPriors = new HashMap<>();
	
	public AbstractTestData(String name, String filename, int precision) {
		this.name = name;
		this.filename = filename;
		this.precision = precision;
	}
	
	protected void addExpectedPrior(String variable, String value, Double proba) {

		Map<String,Double> values = variables2expectedPriors.get(variable);
		if (values == null) {
			values = new HashMap<>();
			variables2expectedPriors.put(variable, values);
		}
		
		values.put(value, proba);
	}
	
	protected void addExpectedPosteriorForEvidence(Map<String,String> evidence, String variable, Map<String,Double> refValues) {
		
		Map<String,Map<String,Double>> forEvidence = evidence2expectedResults.get(evidence);
		if (forEvidence == null) {
			forEvidence = new HashMap<>();
			evidence2expectedResults.put(evidence, forEvidence);
		}
		Map<String,Double> values = forEvidence.get(variable);
		if (values == null) {
			values = new HashMap<>();
			forEvidence.put(variable, values);
		}
		
		values.putAll(refValues);
		
	}
	
	protected void addExpectedPosteriorForEvidence(Map<String,String> evidence, String variable, String value, Double proba) {
		
		Map<String,Map<String,Double>> forEvidence = evidence2expectedResults.get(evidence);
		if (forEvidence == null) {
			forEvidence = new HashMap<>();
			evidence2expectedResults.put(evidence, forEvidence);
		}
		Map<String,Double> values = forEvidence.get(variable);
		if (values == null) {
			values = new HashMap<>();
			forEvidence.put(variable, values);
		}
		
		values.put(value, proba);
	}
	
	@Override
	public final String toString() {
		return this.name;
	}
	
}

