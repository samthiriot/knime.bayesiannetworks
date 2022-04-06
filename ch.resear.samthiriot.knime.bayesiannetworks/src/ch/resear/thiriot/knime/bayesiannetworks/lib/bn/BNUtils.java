package ch.resear.thiriot.knime.bayesiannetworks.lib.bn;

import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;

public class BNUtils {


    /**
     * Takes a spliterator as input, and tries to split it in order to use a given
     * count of threads. 
     * Returns the list of spliterators, which might be smaller than expected if the spliterators 
     * cannot be split enough.
     * Tries to split in equal size. 
     * @param split
     * @param threadsToUse
     * @return
     */
    public static List<Spliterator<?>> splititeratorForParallel(
    		Spliterator<?> split,
    		int threadsToUse) {

		List<Spliterator<?>> spliterators = new LinkedList<>();
		spliterators.add(split);
		List<Spliterator<?>> spliteratorsMaybeSplit = new LinkedList<>();
		spliteratorsMaybeSplit.add(split);
		
		for (int i=1;i<threadsToUse; i++) {
			Spliterator<?> currentPlit = spliteratorsMaybeSplit.remove(0);
			Spliterator<?> subPlit = currentPlit.trySplit();
			if (subPlit == null) {
				// this split refuses to be split
			} else {
    			spliterators.add(subPlit);
				spliteratorsMaybeSplit.add(currentPlit);
				spliteratorsMaybeSplit.add(subPlit);
			}
			if (spliteratorsMaybeSplit.isEmpty())
				// no more splitting hope
				break;
		}
		
		return spliterators;
    }
}
