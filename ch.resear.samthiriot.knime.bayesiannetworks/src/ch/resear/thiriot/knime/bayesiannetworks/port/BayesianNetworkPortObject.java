/**
 * 
 */
package ch.resear.thiriot.knime.bayesiannetworks.port;

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

	
}
