/**
 * 
 */
package ch.resear.thiriot.knime.bayesiannetworks.port;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.ModelContentRO;
import org.knime.core.node.ModelContentWO;
import org.knime.core.node.port.AbstractSimplePortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;

import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;

/**
 * @author sam
 *
 */
public class BayesianNetworkPortObject extends AbstractSimplePortObject {
	
    /**
     * @noreference This class is not intended to be referenced by clients.
     * @since 3.0
     */
    public static final class Serializer extends AbstractSimplePortObjectSerializer<BayesianNetworkPortObject> {}

    /** Convenience accessor for the port type. */
    public static final PortType TYPE = PortTypeRegistry.getInstance().getPortType(BayesianNetworkPortObject.class);

    private CategoricalBayesianNetwork bn;

    /** Empty constructor required by super class, should not be used. */
	public BayesianNetworkPortObject() {

	}
	
	public BayesianNetworkPortObject(CategoricalBayesianNetwork _bn) {
		this.bn = _bn;
	}
	
		
	@Override
	public JComponent[] getViews() {
		
		JScrollPane spNodes;
		{
			StringBuffer sb = new StringBuffer();
			sb.append(bn.nodes.size()).append(" nodes");
			
			if (!bn.nodes.isEmpty()) {
				sb.append(":\n");
				bn.enumerateNodes().stream().map(n -> "- " +n+" "+NodeCategorical.getStrRepresentationOfDomain(n.getDomain())+"\n").forEach(s -> sb.append(s));
			}
			
			JTextArea c = new JTextArea(sb.toString());
			c.setLineWrap(true);
			
			spNodes = new JScrollPane(c); 
			spNodes.setName("Nodes");
		}
		JScrollPane spProbabilities;
		{
			StringBuffer sb = new StringBuffer();
			
			
			if (!bn.nodes.isEmpty()) {
				for (NodeCategorical n: bn.enumerateNodes()) {
					sb.append(n).append("\n");

					n.toStringComplete(sb);
					
					/*
					for (String v: n.getDomain()) {
						//sb.append("p(").append(n.name).append("=").append(v).append("|*) = ").append(n.getConditionalProbability(v)).append("\n");
						
						Collection<NodeCategorical> nodes = new ArrayList<NodeCategorical>(n.getParents());
						IteratorCategoricalVariables it = bn.iterateDomains(nodes);
						while (it.hasNext()) {
							Map<NodeCategorical,String> n2s = it.next();
							sb.append("p( ").append(n.name).append("=").append(v);
							if (n.hasParents()) {
								sb.append(" | ");
								sb.append(n2s.entrySet().stream().map(e -> e.getKey().name+"="+e.getValue()).collect(Collectors.joining(", ")));
							}
							sb.append(" ) = ").append(n.getProbability(v, n2s)).append("\n");
						}
							
							
					}*/
					
					
					sb.append("\n\n");
				
				}
				
			}
			
			JTextArea c = new JTextArea(sb.toString());
			c.setLineWrap(false);
			
			spProbabilities = new JScrollPane(c);
			spProbabilities.setName("Conditional Probability Tables");
		}

		
		return new JComponent [] { spNodes, spProbabilities };
	}
	
	

	/* (non-Javadoc)
	 * @see org.knime.core.node.port.PortObject#getSummary()
	 */
	@Override
	public String getSummary() {
		return "Bayesian Network with "+bn.getNodes().size()+" nodes";
	}

	
	
	/* (non-Javadoc)
	 * @see org.knime.core.node.port.PortObject#getSpec()
	 */
	@Override
	public BayesianNetworkPortSpec getSpec() {
		return new BayesianNetworkPortSpec();
	}

	/* (non-Javadoc)
	 * @see org.knime.core.node.port.AbstractSimplePortObject#save(org.knime.core.node.ModelContentWO, org.knime.core.node.ExecutionMonitor)
	 */
	@Override
	protected void save(ModelContentWO model, ExecutionMonitor exec) throws CanceledExecutionException {
		
		model.addString("bn", bn.getAsXMLString());
		
	}

	/* (non-Javadoc)
	 * @see org.knime.core.node.port.AbstractSimplePortObject#load(org.knime.core.node.ModelContentRO, org.knime.core.node.port.PortObjectSpec, org.knime.core.node.ExecutionMonitor)
	 */
	@Override
	protected void load(ModelContentRO model, PortObjectSpec spec, ExecutionMonitor exec)
			throws InvalidSettingsException, CanceledExecutionException {

		this.bn = CategoricalBayesianNetwork.loadFromXMLBIF(model.getString("bn"));
	}

	public CategoricalBayesianNetwork getBN() {
		return bn;
	}

	@Override
	public boolean equals(Object oport) {
		try {
			return bn.equals(((BayesianNetworkPortObject)oport).bn);	
		} catch (ClassCastException e) {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return bn.hashCode();
	}
	
	

	
}
