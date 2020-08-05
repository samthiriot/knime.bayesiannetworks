package ch.resear.thiriot.knime.bayesiannetworks.port;

import javax.swing.JComponent;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.ModelContentRO;
import org.knime.core.node.ModelContentWO;
import org.knime.core.node.port.AbstractSimplePortObjectSpec;

public class BayesianNetworkPortSpec extends AbstractSimplePortObjectSpec {

	/**
     * @noreference This class is not intended to be referenced by clients.
     * @since 3.0
     */
    public static final class Serializer extends AbstractSimplePortObjectSpecSerializer<BayesianNetworkPortSpec> {}

	public BayesianNetworkPortSpec() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public JComponent[] getViews() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void save(ModelContentWO model) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void load(ModelContentRO model) throws InvalidSettingsException {
		// TODO Auto-generated method stub
		
	}

}
