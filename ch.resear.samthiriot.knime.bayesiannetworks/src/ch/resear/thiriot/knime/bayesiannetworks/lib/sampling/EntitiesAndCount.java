package ch.resear.thiriot.knime.bayesiannetworks.lib.sampling;

import java.util.Map;

import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;

public class EntitiesAndCount {

	public final Map<NodeCategorical,String> node2value;
	public final Integer count;
	
	public EntitiesAndCount(Map<NodeCategorical,String> node2value, Integer count) {
		this.node2value = node2value;
		this.count = count;
	}
	
	@Override
	public String toString() {
		return count + " X "+node2value;
	}
}
