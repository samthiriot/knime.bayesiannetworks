package ch.resear.thiriot.knime.bayesiannetworks.test.lib.bn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.IteratorCategoricalVariables;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.SpliteratorCategoricalVariables;


public class TestIterators {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	protected CategoricalBayesianNetwork getTestBN() {

		CategoricalBayesianNetwork bn = new CategoricalBayesianNetwork("test1");
		
		NodeCategorical nGender = new NodeCategorical(bn, "gender");
		nGender.addDomain("male", "female");
		nGender.setProbabilities(0.55, "male");
		nGender.setProbabilities(0.45, "female");
		
		NodeCategorical nAge = new NodeCategorical(bn, "age");
		nAge.addParent(nGender);
		nAge.addDomain("<15", ">=15");
		nAge.setProbabilities(0.55, "<15", "gender", "male");
		nAge.setProbabilities(0.45, ">=15", "gender", "male");
		nAge.setProbabilities(0.50, "<15", "gender", "female");
		nAge.setProbabilities(0.50, ">=15", "gender", "female");
		
		NodeCategorical nCSP = new NodeCategorical(bn, "CSP");
		nCSP.addParent(nGender);
		nCSP.addDomain("-", "+", "++");
		nCSP.setProbabilities(0.1, "-", "gender", "male");
		nCSP.setProbabilities(0.1, "+", "gender", "male");
		nCSP.setProbabilities(0.8, "++", "gender", "male");
		nCSP.setProbabilities(0.1, "-", "gender", "female");
		nCSP.setProbabilities(0.2, "+", "gender", "female");
		nCSP.setProbabilities(0.7, "++", "gender", "female");
		
		return bn;
	}
	
	@Test
	public void testIteratorCategoricalVariables() {
		
		CategoricalBayesianNetwork bn = getTestBN();
		
		IteratorCategoricalVariables it = bn.iterateDomains();
		
		assertNotNull(it);
		
		// iterate it all
		int count = 0;
		while (it.hasNext()) {
			Map<NodeCategorical, String> next = it.next();
			
			assertNotNull(next);
				
			count++;
		}
		
		// did we got as many iterations as we expected? 
		int expectedCount = 1;
		for (NodeCategorical n: bn.nodes) {
			expectedCount *= n.getDomainSize();
		}
		assertEquals(expectedCount, count);
	}
	
	@Test
	public void testSpliteratorCategoricalVariablesForEach() {
		
		CategoricalBayesianNetwork bn = getTestBN();
		
		SpliteratorCategoricalVariables it = bn.spliterateDomains(null);
		
		assertNotNull(it);
		
		// iterate it all
		List<Map<NodeCategorical, String>> actualEntries = new ArrayList<>();
		it.forEachRemaining(actualEntries::add);
		
		// did we got as many iterations as we expected? 
		int expectedCount = 1;
		for (NodeCategorical n: bn.nodes) {
			expectedCount *= n.getDomainSize();
		}
		assertEquals(expectedCount, actualEntries.size());
		
	}
	
	@Test
	public void testSpliteratorCategoricalVariablesSplittingInTwo() {
		
		CategoricalBayesianNetwork bn = getTestBN();
		
		// we know it has 8 combinations
		
		SpliteratorCategoricalVariables it1 = bn.spliterateDomains(null);
		
		assertNotNull(it1);
		
		SpliteratorCategoricalVariables it2 = it1.trySplit();
		
		assertNotNull(it2);

		// iterate it all
		List<Map<NodeCategorical, String>> actualEntries = new ArrayList<>();
		it1.forEachRemaining(actualEntries::add);
		assertEquals(6, actualEntries.size());

		it2.forEachRemaining(actualEntries::add);
		assertEquals(12, actualEntries.size());

		// did we got as many iterations as we expected? 
		int expectedCount = 1;
		for (NodeCategorical n: bn.nodes) {
			expectedCount *= n.getDomainSize();
		}
		assertEquals(expectedCount, actualEntries.size());
		assertEquals(12, actualEntries.size());

	}
	

	@Test
	public void testSpliteratorCategoricalVariablesSplittingInFour() {
		
		CategoricalBayesianNetwork bn = getTestBN();
		
		SpliteratorCategoricalVariables it1 = bn.spliterateDomains(null);
		
		assertNotNull(it1);
		
		SpliteratorCategoricalVariables it2 = it1.trySplit();
		assertNotNull(it2);

		// should not be splittable anymore
		SpliteratorCategoricalVariables it3 = it1.trySplit();
		assertNull(it3);
		
		SpliteratorCategoricalVariables it4 = it2.trySplit();
		assertNotNull(it4);

		// iterate it all
		List<Map<NodeCategorical, String>> actualEntries = new ArrayList<>();
		it1.forEachRemaining(actualEntries::add);
		assertEquals(4, actualEntries.size());

		it2.forEachRemaining(actualEntries::add);
		assertEquals(8, actualEntries.size());

		//it3.forEachRemaining(actualEntries::add);
		//assertEquals(6, actualEntries.size());

		it4.forEachRemaining(actualEntries::add);
		assertEquals(12, actualEntries.size());

		// did we got as many iterations as we expected? 
		int expectedCount = 1;
		for (NodeCategorical n: bn.nodes) {
			expectedCount *= n.getDomainSize();
		}
		assertEquals(expectedCount, actualEntries.size());
		
	}
	
	@Test
	public void testSpliteratorCategoricalVariablesStream() {
		
		CategoricalBayesianNetwork bn = getTestBN();
		
		// iterate it all
		List<Map<NodeCategorical, String>> actualEntries = new ArrayList<>();
		
		bn.streamDomains(null).forEach(actualEntries::add);

		// did we got as many iterations as we expected? 
		int expectedCount = 1;
		for (NodeCategorical n: bn.nodes) {
			expectedCount *= n.getDomainSize();
		}
		assertEquals(expectedCount, actualEntries.size());
		
	}
	
	@Test
	public void testSpliteratorCategoricalVariablesParallelStream() {
		
		CategoricalBayesianNetwork bn = getTestBN();
		
		// iterate it all
		List<Map<NodeCategorical, String>> actualEntries = new ArrayList<>();
		
		bn.parallelStreamDomains(null).forEach(actualEntries::add);

		// did we got as many iterations as we expected? 
		int expectedCount = 1;
		for (NodeCategorical n: bn.nodes) {
			expectedCount *= n.getDomainSize();
		}
		assertEquals(expectedCount, actualEntries.size());
		
	}

}
