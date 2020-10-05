package ch.resear.thiriot.knime.bayesiannetworks.lib.inference;

import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;

/**
 * Util to monitor the cost of inference computations. Tracks count of multiplication and addition of big decimals
 * 
 * on TestGerland.testBackwardsInferenceFromEvidenceTwo
 * 
 * before optimisation: 							multiplication: 11040, 	additions:6720
 * after skipping useless variables: 				multiplication: 7930, 	additions:4200
 * when not computing neutral op:					multiplication: 7380, 	additions:4200
 * after computing first the variables with 0: 		multiplication: 2440, 	additions:4200
 * after adding every pre computation 				multiplication: 4244, 	additions:7560
 * after skipping useless var alwats				multiplication: 2732, 	additions:4032
 * after caching probabilities once computed: 		multiplication: 1644, 	additions:2688
 * after only computing probabilities on demand:  	multiplication: 1032, 	additions:1512
 * after doing it really for one value				multiplication: 548, 	additions:542
 * 
 * another test
 * ref:												multiplication: 1801, 	additions:3108
 * after not computing the last proba for a domain: multiplication: 1693, 	additions:2910
 * @author sam
 *
 */
public class InferencePerformanceUtils {

	public static final InferencePerformanceUtils singleton = new InferencePerformanceUtils();
	
	public final boolean enabled = true;
	
	private int countMultiply = 0;
	private int countAdditions = 0;
	
	private int cacheHit = 0;
	private int cacheMiss = 0;
	
	
	public void reset() {
		countMultiply = 0;
		countAdditions = 0;
		cacheHit = 0;
		cacheMiss = 0;
	}
	
	public void incAdditions() {
		countAdditions++;
	}
	
	public void incAdditions(int count) {
		countAdditions += count;
	}
	
	public void incMultiplications() {
		countMultiply++;
	}
	
	public void display(ILogger logger) {
		logger.info("multiplication: "+countMultiply+
				" additions:"+countAdditions
				+ ", cache hits:"+cacheHit
				+ " and miss:"+cacheMiss);
	}
	
	private InferencePerformanceUtils() {
		
	}

	public void incCacheMiss() {
		cacheMiss++;
	}

	public void incCacheHit() {
		cacheHit++;		
	}

}
