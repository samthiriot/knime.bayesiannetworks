package ch.resear.thiriot.knime.bayesiannetworks.create.empty;

import java.io.File;
import java.io.IOException;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;

import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortObject;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortSpec;


/**
 * This is the model implementation of XMLBIFBNReader.
 * Read a Bayesian network from the XML BIF format
 *
 * @author Samuel Thiriot
 */
public class CreateEmptyNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger.getLogger(CreateEmptyNodeModel.class);

    /**
     * Constructor for the node model.
     */
    protected CreateEmptyNodeModel() {
                
        super(
	    		// no input
	    		new PortType[]{},
	    		// one output of type BN
	            new PortType[] { BayesianNetworkPortObject.TYPE });
 
    }

   
	@Override
	protected BayesianNetworkPortObject[] execute(
			final PortObject[] inObjects, 
			ExecutionContext exec) throws Exception {
		
		CategoricalBayesianNetwork bn = new CategoricalBayesianNetwork("created");
		
		return new BayesianNetworkPortObject[] { 
				new BayesianNetworkPortObject(bn) 
				};
		

	}

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // nothing to do
    }

    
    @Override
	protected BayesianNetworkPortSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
    	
    	// nothing to do
    	return new BayesianNetworkPortSpec[] { new BayesianNetworkPortSpec() };
	}


	/**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
           
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
       

    }

}

