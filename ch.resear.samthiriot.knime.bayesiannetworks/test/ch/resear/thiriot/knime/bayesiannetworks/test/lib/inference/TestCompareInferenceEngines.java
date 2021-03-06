package ch.resear.thiriot.knime.bayesiannetworks.test.lib.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.LogIntoJavaLogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.AbstractInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.EliminationInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.InferencePerformanceUtils;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.RecursiveConditionningEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.SimpleConditionningInferenceEngine;

/**
 * Compares simulation engines with each other.
 * They should give the same results.
 *  
 * @author Samuel Thiriot
 *
 */
@RunWith(Parameterized.class)
public class TestCompareInferenceEngines {

	public static List<Class<?>> enginesToCompare = Arrays.asList(new Class<?>[] {
		EliminationInferenceEngine.class,
		//BestInferenceEngine.class,
		RecursiveConditionningEngine.class,
		SimpleConditionningInferenceEngine.class
	});
	
	@Parameters(name="{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
        	{ new DataSprinkler() },
        	{ new DataGerland1() },
        	{ new DataCancerSmall() },
        	{ new DataSachs() },
        	// some engines are too slow for this data
        	// { new DataAlarm() },
        	// { new DataBarley() }
           });
    }
    
    @Parameter(0)
    public AbstractTestData data;
    
    private List<AbstractInferenceEngine> engines;
    
    private CategoricalBayesianNetwork bn;
    
    private ILogger logger = LogIntoJavaLogger.getLogger(TestCompareInferenceEngines.class);

    
	@Before
	public void setUp() throws Exception {
	
		System.out.println("\n\n========================================================================================================");

		System.out.println("testing dataset "+data.name);
		System.out.println("loading file "+data.filename);
		bn = CategoricalBayesianNetwork.loadFromXMLBIF(new File(data.filename));
		
		RandomEngine random = new MersenneTwister(new Date());
		
		engines = new LinkedList<>();
		for (Class<?> c: enginesToCompare) {
			engines.add((AbstractInferenceEngine) c.getConstructor(
					ILogger.class, 
					RandomEngine.class,
					CategoricalBayesianNetwork.class).newInstance(
						logger,
						random,
						bn));
		}
		
		System.out.println("creating instances of inference engines to compare: "+enginesToCompare.stream().map(c -> c.getSimpleName()).collect(Collectors.joining(",")));
		System.out.println("========================================================================================================");

		System.out.flush();
		
		
		
	}

	@After
	public void tearDown() throws Exception {
	}


	@Test(timeout=1000)
	public void ensureProbabilityEvidenceNothingIsOne() {
		
		
		for (AbstractInferenceEngine ie: engines) {
			InferencePerformanceUtils.singleton.reset();

			System.err.println("testing "+ie.getClass().getSimpleName());

			Double res = ie.getProbabilityEvidence();
			System.err.println("for "+ie.getClass().getSimpleName()+", Pr({})="+res);
			System.err.flush();
			assertEquals(
					"When no evidence is defined, the Pr(evidence) should be 1.", 
					1.0,
					res.doubleValue(),
					1e-8
					);
			InferencePerformanceUtils.singleton.display(logger);

		}
		
		

	}
		
	public static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    long factor = (long) Math.pow(10, places);
	    value = value * factor;
	    long tmp = Math.round(value);
	    return (double) tmp / factor;
	}
	
	/**
	 * Tests the computation of the probability of evidence for an evidence defined at
	 * the level of the first variable in the network enumeration.
	 */
	@Test(timeout=1000)
	public void testProbabilityOneEvidenceInBNOrder() {
		
		final int rounding = 6;
		
		
		Map<NodeCategorical,String> evidenceForTest = new HashMap<>();
		NodeCategorical nEvidence = bn.enumerateNodes().get(0);
		evidenceForTest.put(nEvidence, nEvidence.getDomain().get(0));
		
		Map<String,Double> engineName2result = new HashMap<>();
		
		for (AbstractInferenceEngine ie: engines) {
			
			InferencePerformanceUtils.singleton.reset();

			// assert evidence
			ie.clearEvidence();
			ie.addEvidence(evidenceForTest);
			
			
			System.err.println("testing "+ie.getClass().getSimpleName());

			Double res = ie.getProbabilityEvidence();
			System.err.println("for "+ie.getClass().getSimpleName()+", Pr("+evidenceForTest+")="+res);
			System.err.flush();
			
			engineName2result.put(ie.getClass().getSimpleName(), round(res, rounding));
			InferencePerformanceUtils.singleton.display(logger);

		}
		
		// compare !
		Map<Double,Long> value2occurences = engineName2result.values().stream()
														.collect(Collectors.groupingBy(p->p, Collectors.counting()));
		
		if (value2occurences.size() > 1) {
			
			System.err.println("frequency table for results: "+value2occurences);
			
			Double ref = Collections.max(value2occurences.entrySet(), Map.Entry.comparingByValue()).getKey();
			
			fail("some engines are not compliant with the dominant answer "+ref+": "+
				engineName2result.entrySet().stream().filter(e -> (e.getValue().doubleValue() != ref.doubleValue()))
					.map(e -> e.getKey()+"="+e.getValue())
					.collect(Collectors.joining(",")));
		} else {
			System.err.println("all the inference engine agree that Pr("+evidenceForTest+")="+value2occurences.keySet().iterator().next());
		}
	

	}
	

	/**
	 * Tests the computation of the probability of two peaces of evidence defined at
	 * the level of the first variable in the network enumeration.
	 */
	@Test(timeout=1000)
	public void testProbabilityTwoEvidenceInBNOrder() {
		
		final int rounding = 6;
		
		if (bn.getNodes().size() <= 2)
			// cannot test, this BN is too small !
			return;
		
		Map<NodeCategorical,String> evidenceForTest = new HashMap<>();
		NodeCategorical nEvidence = bn.enumerateNodes().get(0);
		evidenceForTest.put(nEvidence, nEvidence.getDomain().get(0));
		nEvidence = bn.enumerateNodes().get(1);
		evidenceForTest.put(nEvidence, nEvidence.getDomain().get(0));

		Map<String,Double> engineName2result = new HashMap<>();
		
		for (AbstractInferenceEngine ie: engines) {
			
			InferencePerformanceUtils.singleton.reset();

			// assert evidence
			ie.clearEvidence();
			ie.addEvidence(evidenceForTest);
			
			
			System.err.println("testing "+ie.getClass().getSimpleName());

			Double res = ie.getProbabilityEvidence();
			System.err.println("for "+ie.getClass().getSimpleName()+", Pr("+evidenceForTest+")="+res);
			System.err.flush();
			
			engineName2result.put(ie.getClass().getSimpleName(), round(res, rounding));
			InferencePerformanceUtils.singleton.display(logger);

		}
		
		// compare !
		Map<Double,Long> value2occurences = engineName2result.values().stream()
														.collect(Collectors.groupingBy(p->p, Collectors.counting()));
		
		if (value2occurences.size() > 1) {
			
			System.err.println("frequency table for results: "+value2occurences);
			
			Double ref = Collections.max(value2occurences.entrySet(), Map.Entry.comparingByValue()).getKey();
			
			fail("some engines are not compliant with the dominant answer "+ref+": "+
				engineName2result.entrySet().stream().filter(e -> (e.getValue().doubleValue() != ref.doubleValue()))
					.map(e -> e.getKey()+"="+e.getValue())
					.collect(Collectors.joining(",")));
		} else {
			System.err.println("all the inference engine agree that Pr("+evidenceForTest+")="+value2occurences.keySet().iterator().next());
		}
	

	}
	

	/**
	 * Tests the computation of the probability of two peaces of evidence defined at
	 * the level of the first variable in the network enumeration.
	 */
	@Test //(timeout=1000)
	public void testProbabilityOneEvidenceInLastNode() {
		
		final int rounding = 5;
	
		Map<NodeCategorical,String> evidenceForTest = new HashMap<>();
		List<NodeCategorical> bnOrder = bn.enumerateNodes();
		NodeCategorical nEvidence = bnOrder.get(bnOrder.size()-1);
		evidenceForTest.put(nEvidence, nEvidence.getDomain().get(0));

		Map<String,Double> engineName2result = new HashMap<>();
		
		for (AbstractInferenceEngine ie: engines) {
			
			InferencePerformanceUtils.singleton.reset();

			// assert evidence
			ie.clearEvidence();
			ie.addEvidence(evidenceForTest);
			
			
			System.err.println("testing "+ie.getClass().getSimpleName());

			Double res = ie.getProbabilityEvidence();
			System.err.println("for "+ie.getClass().getSimpleName()+", Pr("+evidenceForTest+")="+res);
			System.err.flush();
			
			engineName2result.put(ie.getClass().getSimpleName(), round(res, rounding));
			InferencePerformanceUtils.singleton.display(logger);

		}
		
		// compare !
		Map<Double,Long> value2occurences = engineName2result.values().stream()
														.collect(Collectors.groupingBy(p->p, Collectors.counting()));
		
		if (value2occurences.size() > 1) {
			
			System.err.println("frequency table for results: "+value2occurences);
			
			Double ref = Collections.max(value2occurences.entrySet(), Map.Entry.comparingByValue()).getKey();
			
			fail("some engines are not compliant with the dominant answer "+ref+": "+
				engineName2result.entrySet().stream().filter(e -> (e.getValue().doubleValue() != ref.doubleValue()))
					.map(e -> e.getKey()+"="+e.getValue())
					.collect(Collectors.joining(",")));
		} else {
			System.err.println("all the inference engine agree that Pr("+evidenceForTest+")="+value2occurences.keySet().iterator().next());
		}
	

	}
	

	/**
	 * Tests the computation of the probability of two peaces of evidence defined at
	 * the level of the first variable in the network enumeration.
	 */
	@Test(timeout=1000)
	public void testProbabilityOneEvidenceInTwoLastNodes() {
		
		final int rounding = 6;
		
		if (bn.getNodes().size() <= 2)
			// cannot test, this BN is too small !
			return;
		
		Map<NodeCategorical,String> evidenceForTest = new HashMap<>();
		List<NodeCategorical> bnOrder = bn.enumerateNodes();
		NodeCategorical nEvidence = bnOrder.get(bnOrder.size()-1);
		evidenceForTest.put(nEvidence, nEvidence.getDomain().get(0));
		nEvidence = bnOrder.get(bnOrder.size()-2);
		evidenceForTest.put(nEvidence, nEvidence.getDomain().get(0));

		Map<String,Double> engineName2result = new HashMap<>();
		
		for (AbstractInferenceEngine ie: engines) {
			
			InferencePerformanceUtils.singleton.reset();

			// assert evidence
			ie.clearEvidence();
			ie.addEvidence(evidenceForTest);
			
			System.err.println("testing "+ie.getClass().getSimpleName());

			Double res = ie.getProbabilityEvidence();
			System.err.println("for "+ie.getClass().getSimpleName()+", Pr("+evidenceForTest+")="+res);
			System.err.flush();
			
			engineName2result.put(ie.getClass().getSimpleName(), round(res, rounding));
			InferencePerformanceUtils.singleton.display(logger);

		}
		
		// compare !
		Map<Double,Long> value2occurences = engineName2result.values().stream()
														.collect(Collectors.groupingBy(p->p, Collectors.counting()));
		
		if (value2occurences.size() > 1) {
			
			System.err.println("frequency table for results: "+value2occurences);
			
			Double ref = Collections.max(value2occurences.entrySet(), Map.Entry.comparingByValue()).getKey();
			
			fail("some engines are not compliant with the dominant answer "+ref+": "+
				engineName2result.entrySet().stream().filter(e -> (e.getValue().doubleValue() != ref.doubleValue()))
					.map(e -> e.getKey()+"="+e.getValue())
					.collect(Collectors.joining(",")));
		} else {
			System.err.println("all the inference engine agree that Pr("+evidenceForTest+")="+value2occurences.keySet().iterator().next());
		}
	

	}
	

}
