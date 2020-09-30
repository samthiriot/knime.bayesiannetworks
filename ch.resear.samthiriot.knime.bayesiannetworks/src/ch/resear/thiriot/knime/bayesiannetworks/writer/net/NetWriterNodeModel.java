package ch.resear.thiriot.knime.bayesiannetworks.writer.net;

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
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;

import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortObject;


/**
 * TODO overrite checkbox
 * 
 * This is the model implementation of BNXMLNBIFWriterNode.
 * Writes the Bayesian network into an XML BIF file.
 *
 * @author Samuel Thiriot
 */
public class NetWriterNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(NetWriterNodeModel.class);

    private final SettingsModelString m_file = new SettingsModelString("filename", null);


    /**
     * Constructor for the node model.
     */
    protected NetWriterNodeModel() {

        super(
	    		// one BN as input
	    		new PortType[]{ BayesianNetworkPortObject.TYPE },
	    		// no output
	            new PortType[] { }
	    		);
        
    }


	@Override
	protected PortObject[] execute(
			final PortObject[] inObjects, 
			ExecutionContext exec) throws Exception {

		// retrieve the BN from the input
    	CategoricalBayesianNetwork bn = null;
    	try {
    		BayesianNetworkPortObject capsule = (BayesianNetworkPortObject)inObjects[0];
    		bn = capsule.getBN();
    	} catch (ClassCastException e) {
    		throw new IllegalArgumentException("The input should be a Bayesian network", e);
    	}
    	
    	// load from parameters the file to process
		File fileData = new File(m_file.getStringValue());
		
		bn.saveAsNet(fileData);
		
		
		return new PortObject[] {};
		

	}


    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
     
    }
    
    @Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
	
        return new PortObjectSpec[]{};
	}
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        
    	m_file.saveSettingsTo(settings);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
           
        
    	m_file.loadSettingsFrom(settings);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
    	m_file.validateSettings(settings);

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

