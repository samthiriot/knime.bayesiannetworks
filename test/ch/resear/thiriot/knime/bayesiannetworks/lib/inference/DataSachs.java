package ch.resear.thiriot.knime.bayesiannetworks.lib.inference;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DataSachs extends AbstractTestData {

	public DataSachs() {
		super(
				"Sachs", 
				"./resources/test/bayesiannetworks/sachs.xmlbif", 
				3
				);
		
		// add expected data
		Map<String,String> evidence = null;
		
		// with no inference
		evidence = Collections.emptyMap();
		
		// with evidence
		
		// case 1
		evidence = new HashMap<>();
		evidence.put("PIP3", "1");
		evidence.put("Akt", "2");
		this.addExpectedPosteriorForEvidence(evidence, "Plcg", "2", 0.0284);
		this.addExpectedPosteriorForEvidence(evidence, "Plcg", "3", 0.1940);
		this.addExpectedPosteriorForEvidence(evidence, "Jnk", "1", 0.5309);

		


	}

}
