package ch.resear.thiriot.knime.bayesiannetworks.reader;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

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
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortSpec;


/**
 * This is the model implementation of XMLBIFBNReader.
 * Read a Bayesian network from the XML BIF format
 *
 * @author Samuel Thiriot
 */
public class XMLBIFBNReaderNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger.getLogger(XMLBIFBNReaderNodeModel.class);


    // example value: the models count variable filled from the dialog 
    // and used in the models execution method. The default components of the
    // dialog work with "SettingsModels".
    private final SettingsModelString m_file = new SettingsModelString("filename", null);
    

    /**
     * Constructor for the node model.
     */
    protected XMLBIFBNReaderNodeModel() {
                
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

    	// we have no input port
    	
    	// load from parameters the file to process
		File fileData = new File(m_file.getStringValue());
		
		logger.info("opening as a Bayesian network: "+fileData.getName());

		CategoricalBayesianNetwork bn = CategoricalBayesianNetwork.loadFromXMLBIF(fileData);
		
		logger.info("parsed a Bayesian network with "+
					bn.getNodes().size()+" variables: "+
					bn.getNodes().stream().map(v->v.getName()).collect(Collectors.joining(","))
					);
		
		return new BayesianNetworkPortObject[] { 
				new BayesianNetworkPortObject(bn) 
				};
		

	}

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // TODO Code executed on reset.
        // Models build during execute are cleared here.
        // Also data handled in load/saveInternals will be erased here.
    }

    
    @Override
	protected BayesianNetworkPortSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
    	
    	if (m_file.getStringValue().isEmpty())
    		throw new InvalidSettingsException("please provide a Bayesian network to read");
    	
    	File fileData = new File(m_file.getStringValue());
    	if (!fileData.isFile() || !fileData.exists())
    		throw new InvalidSettingsException("the filename does not refer to a file");
    	if (!fileData.canRead())
    		throw new InvalidSettingsException("cannot read file "+m_file);
    	
    	return new BayesianNetworkPortSpec[] { new BayesianNetworkPortSpec() };
	}


	/**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {

        // TODO save user settings to the config object.
        
    	m_file.saveSettingsTo(settings);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        // TODO load (valid) settings from the config object.
        // It can be safely assumed that the settings are valided by the 
        // method below.
        
    	m_file.loadSettingsFrom(settings);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        // TODO check if the settings could be applied to our model
        // e.g. if the count is in a certain range (which is ensured by the
        // SettingsModel).
        // Do not actually set any values of any member variables.

    	m_file.validateSettings(settings);

    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        
        // TODO load internal data. 
        // Everything handed to output ports is loaded automatically (data
        // returned by the execute method, models loaded in loadModelContent,
        // and user settings set through loadSettingsFrom - is all taken care 
        // of). Load here only the other internals that need to be restored
        // (e.g. data used by the views).

    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
       
        // TODO save internal models. 
        // Everything written to output ports is saved automatically (data
        // returned by the execute method, models saved in the saveModelContent,
        // and user settings saved through saveSettingsTo - is all taken care 
        // of). Save here only the other internals that need to be preserved
        // (e.g. data used by the views).

    }

}

