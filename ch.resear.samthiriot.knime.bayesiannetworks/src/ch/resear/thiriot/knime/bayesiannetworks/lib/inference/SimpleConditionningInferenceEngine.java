package ch.resear.thiriot.knime.bayesiannetworks.lib.inference;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.map.LRUMap;

import cern.jet.random.engine.RandomEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.IteratorCategoricalVariables;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;

/**
 * Simple conditioning stands as the simplest exact inference engine possible to propagate evidence 
 * in Bayesian Networks. Can only be used on small Bayesian networks - is untractable on bigger ones. 
 * Use it one simple cases, or as a benchmark. Was validated against test networks and inference engines in samiam. 
 * 
 * 
 * It just compute posterior probabilities of every variable based on 
 * either its original probabilities, or based on evidence on the variable or on one parent of the variable. 
 * 
 * Only computes probabilities on demand, so few demands will lead to few computations.
 * Caches all the computed probabilities so many demands will not increase computations anymore - at the expense of memory, as usual.
 * 
 * Simple optimizations were implemented, including: <ul>
 * <li>excluding irrelevant variables during computation</li>
 * <li>stop multiplications as soon as a zero is met</li>
 * <li>select order of variables based on their likelihood of bringing zeros</li>
 * <li>do not compute the last probability of a domain but rely of the complement to 1 instead</li>
 * <li>Cache normalization factors, which corresponds to variable elimination</li>
 * </ul>
 * 
 * @author Samuel Thiriot
 *
 */
public class SimpleConditionningInferenceEngine extends AbstractInferenceEngine {

	private Map<NodeCategorical,double[]> computed = new HashMap<>();
	
	// note that cache 1 and 2 are combinatory: for each cache level 1, there will be CACHE_MAXITEMS2 level2!
	private static int CACHE_MAXITEMS = 100;
	private static int CACHE_MAXITEMS2 = 100;

	private static int CACHE_EVIDENCE = 5000;

	
	private LRUMap<Map<NodeCategorical,String>,Map<Set<NodeCategorical>,Double>> known2nuisance2value = null;
	private LRUMap<Map<NodeCategorical,String>,Double> evidence2proba = null;

	final boolean debug;

	public SimpleConditionningInferenceEngine(
			ILogger logger, 
			RandomEngine random,
			CategoricalBayesianNetwork bn) {
		super(logger, random, bn);

		debug = logger.isDebugEnabled();
	}


	@Override
	protected double retrieveConditionalProbability(NodeCategorical n, String s) {
			
		if (debug)
			logger.debug("p("+n.name+"="+s+"|"+evidenceVariable2value);

		// can we even compute it ? 
		// TODO ???if (blacklisted.contains(n))
		//	throw new IllegalArgumentException("cannot compute the probability "+n+"="+s+" with this evidence "+variable2value+": the evidence is posterior this node, and this engine is not able to deal with backpropagation");
		
		// is it part of evidence ?
		{
			String ev = evidenceVariable2value.get(n);
			if (ev != null) {
				if (ev.equals(s))
					return 1.;
				else 
					return 0.;
			}
		}
		
		// did we computed it already ?
		double[] done = computed.get(n);
		// did we stored anything for this node ? (if not, prepare for it)
		if (done == null) {
			done = new double[n.getDomainSize()];
			Arrays.fill(done, -1.);
			computed.put(n, done);
		} else {
			double res = done[n.getDomainIndex(s)];
			if (res > 0)
				return res;
		}
		
		double res;

		// we did not computed this value.
		// maybe we know everything but this one ?
		int known = 0;
		for (int i=0; i<done.length; i++) {
			if (done[i] >= 0){ 
				known++;
			}
		}
		if (known == done.length-1) {
			// we know all the values but one
			if (debug)
				logger.debug("we can save one computation here by doing p(X=x)=1 - sum(p(X=^x))");
			double total = 1.;
			for (double d : done) {
				if (d>=0)
					total -= d;
			}
			res = total;
		} else {
			if (debug)
				logger.debug("no value computed for p("+n.name+"="+s+"|"+evidenceVariable2value+"), starting computation...");
			//res = n.getConditionalProbabilityPosterior(s, variable2value, computed);
			res = computePosteriorConditionalProbability(n, s, evidenceVariable2value);
		}
		done[n.getDomainIndex(s)] = res;
		if (debug)
			logger.debug("returning p("+n.name+"="+s+"|"+evidenceVariable2value+")="+res);

		return res;
		
	}
	

	@Override
	protected double[] retrieveConditionalProbability(NodeCategorical n) {
		
		double[] done;
	
		// is it part of evidence ?
		{
			String ev = evidenceVariable2value.get(n);
			if (ev != null) {
				done = new double[n.getDomainSize()];
				for (int i=0; i<n.getDomainSize(); i++) {
					String v = n.getValueIndexed(i);
					if (ev.equals(v)) {
						done[i] = 1.; 
						break; // we can break there in fact (initialized to 0 !)
					} else
						done[i] = 0.;
				}
				return done;

			}
		}
		
		// did we computed it already ?
		done = computed.get(n);
		// did we stored anything for this node ? (if not, prepare for it)
		if (done != null) {
			return done;
		}
		
		// did we computed that specific one ? if not, compute it
		if (done == null) {
			
			done = computePosteriorConditionalProbability(n, evidenceVariable2value);
			
			computed.put(n, done);
		}
		if (debug)
			logger.debug("returning p("+n.name+"=*|"+evidenceVariable2value+") : "+done);

		return done;
		
	}


	/**
	 * 
	 * @param nodes
	 */
	protected Set<NodeCategorical> getLeaf(Set<NodeCategorical> nodes) {
		
		if (debug)
			logger.debug("searching for the leafs of " + nodes);
		
		Set<NodeCategorical> leafs = new HashSet<>(nodes);
		
		for (NodeCategorical n: nodes) {
			leafs.removeAll(n.getParents());
		}
		
		if (debug) 
			logger.debug("leafs of "+nodes+" are " + leafs);

		return leafs;
		
	}

	
	private Double getCached(
			Map<NodeCategorical,String> known, 
			Set<NodeCategorical> nuisance
			) {
		
		if (known2nuisance2value == null)
			known2nuisance2value = new LRUMap<>(CACHE_MAXITEMS);
		
		Map<Set<NodeCategorical>,Double> res = known2nuisance2value.get(known);
		if (res == null) {
			InferencePerformanceUtils.singleton.incCacheMiss();
			return null;
		}
		InferencePerformanceUtils.singleton.incCacheHit();
		return res.get(nuisance);
		
	}
	
	private void storeCache(
			Map<NodeCategorical,String> known, 
			Set<NodeCategorical> nuisance,
			Double d
			) {
		Map<Set<NodeCategorical>,Double> res = known2nuisance2value.get(known);
		if (res == null) {
			res = new LRUMap<>(CACHE_MAXITEMS2);
			known2nuisance2value.put(known, res);
		}
		res.put(nuisance, d);
	}
	
	/**
	 * Given a set of known values for variables, and the list of the remaining variables not 
	 * covered by this evidence (refered to as nuisance variables),
	 * sums probabilities over the relevant variables to computed the expected probability.
	 * @param known
	 * @param node2probabilities 
	 */
	protected double sumProbabilities(
			Map<NodeCategorical,String> known, 
			Set<NodeCategorical> nuisanceRaw) {
		
		
		Set<NodeCategorical> nuisanceS = new HashSet<>(nuisanceRaw);
		nuisanceS.removeAll(known.keySet());
		
		// quick exit
		if (nuisanceRaw.isEmpty() && known.isEmpty())
			return 1.;
		
		// is it cached ?
		Double res = getCached(known, nuisanceS); // optimisation: cache !
		if (res != null)
			return res;
		
		res = 0.;
					
		if (debug)
			logger.debug("summing probabilities for nuisance "+known+", and known "+nuisanceS);

		for (IteratorCategoricalVariables it = bn.iterateDomains(nuisanceS); it.hasNext(); ) {
			
			Map<NodeCategorical,String> n2v = it.next();
			n2v.putAll(known);
			
			double p = this.bn.jointProbability(n2v, Collections.emptyMap());
			
			if (debug)
				logger.debug("p("+n2v+")="+p);
			
			res += p;
			InferencePerformanceUtils.singleton.incAdditions();

			// if over one, stop.
			if (res >= 1) {
				res = 1.;
				break;
			}

		}
		
		storeCache(known, nuisanceS, res);
		
		if (debug)
			logger.debug("total " + res);
		return res;
	}
	
	
	/**
	 * For a given node, computes the probabilities accounting prior probabilities 
	 * and evidence (of parents or children).
	 * Returns the probabilities for each value of the domain.
	 * Only computes if the value is not already present in cache 
	 * @param n
	 */
	protected double[] computePosteriorConditionalProbability(
											NodeCategorical n, 
											Map<NodeCategorical,String> evidence) {
		final int domainSize = n.getDomainSize();

		double[] v2p = new double[domainSize];

		double pFree = getProbabilityEvidence(); // this.sumProbabilities(evidence, selectRelevantVariables((NodeCategorical)null, evidence, bn.nodes)); // optimisation: elimination of irrelevant variables

		for (int i=0; i<domainSize; i++) {
			String nv = n.getValueIndexed(i);
		
			if (debug)
				logger.debug("computing p(*=*|"+n.name+"="+nv+")");
							
			Map<NodeCategorical,String> punctualEvidence = new HashMap<>(evidence);
			punctualEvidence.put(n, nv);
						
			double p = this.sumProbabilities(
					punctualEvidence, 
					selectRelevantVariables(n, evidence, bn.nodes) // optimisation: elimination of irrelevant variables
					);
						
			if (debug) {
				logger.debug("computed p("+n.name+"="+nv+"|"+punctualEvidence+","+n.name+"="+nv+")="+p);
				logger.debug("computed p(*=*|"+n.name+"="+nv+")="+p);
			}
			v2p[i] = p;
			
		}
		
		if (debug)
			logger.debug("now computing the overall probas");

		for (int i=0; i<domainSize; i++) {
			String nv = n.getValueIndexed(i);

			double p = v2p[i];
			
			double pp = p / pFree;
			v2p[i] = pp;
			if (debug)
				logger.debug("computed p({"+n.name+"="+nv+"|evidence)= p("+n.name+"="+nv+"|evidence)/p("+n.name+"|evidence)="+p+"/"+pFree+"="+pp);
		}
		
		
		return v2p;
	}
	
	

	/**
	 * For a given node and a given value in its discrete domain, computes its probability accounting 
	 * evidence and prior probailities 
	 * already computed beforehand.
	 * At the end, returns the probabilities for each value of the domain. 
	 * @param n
	 */
	protected double computePosteriorConditionalProbability(
											NodeCategorical n, 
											String nv,
											Map<NodeCategorical,String> evidence) {
						
		double pFree = this.sumProbabilities(
				evidence, 
				selectRelevantVariables((NodeCategorical)null, 
						evidence, 
						bn.nodes)); // optimisation: elimination of irrelevant variables

		if (debug)
			logger.debug("computing p(*=*|"+n.name+"="+nv+")");
						
		Map<NodeCategorical,String> punctualEvidence = new HashMap<>(evidence);
		punctualEvidence.put(n, nv);
					
		double p = this.sumProbabilities(
				punctualEvidence, 
				selectRelevantVariables(n, evidence, bn.nodes) // optimisation: elimination of irrelevant variables
				);
			
		if (debug) {
			logger.debug("computed p("+n.name+"="+nv+"|"+punctualEvidence+","+n.name+"="+nv+")="+p);
			
			logger.debug("computed p(*=*|"+n.name+"="+nv+")="+p);
						
			logger.debug("now computing the overall probas");
		}
		
		double pp;
		try {
			pp = p / pFree;
		} catch (ArithmeticException e) {
			logger.error("unable to compute probability p("+n.name+"="+nv+"|*): pfree="+pFree+", p="+p);
			pp = 0.; // TODO ???
		}
		
		
		if (debug)
			logger.debug("computed p("+n.name+"="+nv+"|evidence)= p("+n.name+"="+nv+"|evidence)/p("+n.name+"|evidence)="+p+"/"+pFree+"="+pp);
		
		return pp;
	}
	

	
	
	@Override
	public void compute() {
				
		computed.clear();


		// TODO can we detect easily conflicting evidence ?
		

		// mark it clean
		super.compute();
		
	}


	@Override
	protected double computeProbabilityEvidence() {

		if (evidence2proba == null)
			evidence2proba = new LRUMap<>(CACHE_EVIDENCE);
		
		Double res = evidence2proba.get(evidence2proba);
		
		if (res == null) {
			InferencePerformanceUtils.singleton.incCacheMiss();
			res = this.sumProbabilities(evidenceVariable2value, selectRelevantVariables((NodeCategorical)null, evidenceVariable2value, bn.nodes)); // optimisation: elimination of irrelevant variables
			evidence2proba.put(evidenceVariable2value, res);
		} else {
			InferencePerformanceUtils.singleton.incCacheHit();
		}

		return res;
	}



	
	
}
