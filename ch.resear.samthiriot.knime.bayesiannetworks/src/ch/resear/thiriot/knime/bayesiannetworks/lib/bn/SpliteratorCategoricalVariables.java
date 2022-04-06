package ch.resear.thiriot.knime.bayesiannetworks.lib.bn;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;

/**
 * Iterates over the combinations of values for the domains of each variable passed at initialization.
 * 
 * @author Samuel Thiriot
 *
 */
public final class SpliteratorCategoricalVariables implements Spliterator<Map<NodeCategorical,String>> {

	
	/**
	 * the nodes we have to explore the values for 
	 */
	private final NodeCategorical[] nuisance;
	
	/**
	 * We arbitrarly select one node on which we will split. 
	 * Then we only split this domain, which splits the complete combinations into subparts. 
	 */
	private final int idxNodeForSplit;
	
	/**
	 * the current values we are exploring for each node
	 */
	private final int[] nodeIdx2valueIdx;

	/**
	 * The min values we should explore for each node 
	 */
	private final int[] nodeIdx2minValueIdx;
	
	/**
	 * The max values we should explore for each node 
	 */
	private final int[] nodeIdx2maxValueIdx;
	
	
	private boolean hasNext;
	
	/** 
	 * used to check if the execution should be canceled
	 */
	private final ExecutionContext ex;
	
	
	public SpliteratorCategoricalVariables(
			Collection<NodeCategorical> variables,
			ExecutionContext ex) {
		
		this.ex = ex;
		
		// store the variables in a way which can be accessed quickly in an indexed way
		this.nuisance = new NodeCategorical[variables.size()];
		variables.toArray(nuisance);
		
		// store the initial indices we explore (initialized at 0)
		this.nodeIdx2valueIdx = new int[nuisance.length];

		// prepare the min indices as a bunch of 0
		this.nodeIdx2minValueIdx = new int[nuisance.length];

		// prepare the max indices from the original nodes domains
		this.nodeIdx2maxValueIdx = new int[nuisance.length];
		for (int cursorParents = 0; cursorParents < nuisance.length; cursorParents++) {
			this.nodeIdx2maxValueIdx[cursorParents] = nuisance[cursorParents].getDomainSize();
		}
		
		// find the node we will split on
		// we will just take the biggest one (and the last one if equality), as it provides the best subspliting potential :-)
		{
			int maxSizeFound = 0;
			int idxBiggestFound = -1;
			for (int cursorParents = 0; cursorParents < nuisance.length; cursorParents++) {
				if (nuisance[cursorParents].getDomainSize() >= maxSizeFound)
					idxBiggestFound = cursorParents;
			} 
			idxNodeForSplit = idxBiggestFound;
		}
		
		// at the beginning, we have something to explore if we have at least one variable having a value in its domain
		this.hasNext = true;
		
	}
	
	/**
	 * Used internally to create the spliterator on split.
	 */
	protected SpliteratorCategoricalVariables(
			NodeCategorical[] nuisance,
			int idxNodeForSplit,
			int[] nodeIdx2valueIdx,
			int[] nodeIdx2minValueIdx,
			int[] nodeIdx2maxValueIdx,
			ExecutionContext ex
			) {
		
		this.nuisance = nuisance.clone();
		this.idxNodeForSplit = idxNodeForSplit;
		this.nodeIdx2valueIdx = nodeIdx2valueIdx;
		this.nodeIdx2minValueIdx = nodeIdx2minValueIdx;
		this.nodeIdx2maxValueIdx = nodeIdx2maxValueIdx;
		this.hasNext = true;
		this.ex = ex;
	}


	@Override
	public boolean tryAdvance(Consumer<? super Map<NodeCategorical, String>> action) {
	
		try {
			if (ex != null) 
				ex.checkCanceled();
		} catch (CanceledExecutionException e) {
			return false;
		}
		
		if (!hasNext)
			// sorry, we reached the end of our journey here
			return false;
		
		// create the combination we explore now
		Map<NodeCategorical,String> n2v = new HashMap<>(nuisance.length);
		for (int i=0; i<nodeIdx2valueIdx.length; i++) {
			n2v.put(nuisance[i], nuisance[i].getValueIndexed(nodeIdx2valueIdx[i]));
		}
	
		// TODO run it (probably try catch, mmm?)
		action.accept(n2v);
		
		// skip to the next index
		int cursorParents = nodeIdx2valueIdx.length-1;
		if (nodeIdx2valueIdx.length == 0) {
			hasNext = false; 
		} else {
			nodeIdx2valueIdx[cursorParents]++;
			// ... if we are at the max of the domain size of the current node, then shift back
			while (nodeIdx2valueIdx[cursorParents] >= nodeIdx2maxValueIdx[cursorParents]) {
				nodeIdx2valueIdx[cursorParents] = nodeIdx2minValueIdx[cursorParents];
				cursorParents--;
				// maybe we finished the exploration ?
				if (cursorParents < 0) {
					hasNext = false;
					break;
				}
				// skip to the next one 
				nodeIdx2valueIdx[cursorParents]++;
			}
		}
		
		return hasNext;
	}

	@Override
	public SpliteratorCategoricalVariables trySplit() {
		
		try {
			if (ex != null) 
				ex.checkCanceled();
		} catch (CanceledExecutionException e) {
			return null;
		}
		
		final int remainingToRunHere = nodeIdx2maxValueIdx[idxNodeForSplit] - nodeIdx2valueIdx[idxNodeForSplit];
		
		//System.out.println("splitting "+remainingToRunHere);
		
		// can we split more?
		if (!hasNext || remainingToRunHere < 2)
			// sorry, but there is only work for me here
			return null;
		
		// let's split
		final int idxSplit = nodeIdx2valueIdx[idxNodeForSplit] + remainingToRunHere/2;
		//System.out.println("splitting at idx "+idxSplit+" between "+nodeIdx2valueIdx[idxNodeForSplit]+" and "+nodeIdx2maxValueIdx[idxNodeForSplit]);
		
		// the other one will deal with the remaining one 
		int[] _nodeIdx2valueIdx = nodeIdx2valueIdx.clone();
		int[] _nodeIdx2minValueIdx = nodeIdx2minValueIdx.clone();
		int[] _nodeIdx2maxValueIdx = nodeIdx2maxValueIdx.clone();
		
		// the other one will go to the very end of the domain
		_nodeIdx2maxValueIdx[idxNodeForSplit] = nodeIdx2maxValueIdx[idxNodeForSplit];
		// but will start half way 
		_nodeIdx2valueIdx[idxNodeForSplit] = idxSplit;
		_nodeIdx2minValueIdx[idxNodeForSplit] = idxSplit;
		
		// this one will do the first part of the domain 
		nodeIdx2maxValueIdx[idxNodeForSplit] = idxSplit;
		
		System.out.println("this one will go from "+nodeIdx2minValueIdx[idxNodeForSplit]+" to "+nodeIdx2maxValueIdx[idxNodeForSplit]+" (now "+nodeIdx2valueIdx[idxNodeForSplit]+")");
		System.out.println("the other will go from "+_nodeIdx2minValueIdx[idxNodeForSplit]+" to "+_nodeIdx2maxValueIdx[idxNodeForSplit]+" (now "+_nodeIdx2valueIdx[idxNodeForSplit]+")");

		System.out.println("splitting on "+nuisance[idxNodeForSplit].name);
		System.out.println("this one will iterate " + IntStream
				.range(nodeIdx2minValueIdx[idxNodeForSplit], nodeIdx2maxValueIdx[idxNodeForSplit])
				.mapToObj(i -> nuisance[idxNodeForSplit].domain.get(i))
				.collect(Collectors.joining(",")));
		System.out.println("the other will iterate " + IntStream
				.range(_nodeIdx2minValueIdx[idxNodeForSplit], _nodeIdx2maxValueIdx[idxNodeForSplit])
				.mapToObj(i -> nuisance[idxNodeForSplit].domain.get(i))
				.collect(Collectors.joining(",")));
		
		
		// return the other spliterator
		return new SpliteratorCategoricalVariables(
				nuisance, 
				idxNodeForSplit, 
				_nodeIdx2valueIdx, 
				_nodeIdx2minValueIdx,
				_nodeIdx2maxValueIdx,
				ex
				);
	
	}

	@Override
	public long estimateSize() {
		long total = 1;
		for (int i=0; i<nodeIdx2valueIdx.length; i++) {
			total *= nodeIdx2maxValueIdx[i] - nodeIdx2valueIdx[i] + 1;
		}
		return total;
	}

	@Override
	public int characteristics() {
		return Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.ORDERED;
	}

	@Override
	public String toString() {
		return "spliterator over "+nuisance.length+" variables (split "+nuisance[idxNodeForSplit].name+" in "+nodeIdx2minValueIdx[idxNodeForSplit]+":"+nodeIdx2maxValueIdx[idxNodeForSplit]+")";
	}

	
	
	
}
