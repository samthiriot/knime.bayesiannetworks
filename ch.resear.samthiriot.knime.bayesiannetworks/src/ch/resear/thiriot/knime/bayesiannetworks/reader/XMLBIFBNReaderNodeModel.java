package ch.resear.thiriot.knime.bayesiannetworks.reader;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
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
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.FileUtil;

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

   
    private File getInputFile() throws InvalidSettingsException {
    	
    	// load from parameters the file to process
    	final String filename = m_file.getStringValue();
    	if (filename == null)
	    	throw new InvalidSettingsException("No filename provided");
		
        CheckUtils.checkSourceFile(filename);

        // convert to an url, so we accept path relative to workflows
        URL url;
		try {
			url = FileUtil.toURL(filename);
		} catch (InvalidPathException | MalformedURLException e2) {
			e2.printStackTrace();
			throw new InvalidSettingsException("unable to open URL "+filename+": "+e2.getMessage());
		}
		
		return FileUtil.getFileFromURL(url);
		
    }
    
	@Override
	protected BayesianNetworkPortObject[] execute(
			final PortObject[] inObjects, 
			ExecutionContext exec) throws Exception {

    	// we have no input port
    	
    	// load from parameters the file to process

		File fileData = getInputFile();
       
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


    }

    
    @Override
	protected BayesianNetworkPortSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
    	

    	// load from parameters the file to process
    	final String filename = m_file.getStringValue();
    	if (filename == null || filename.isEmpty())
    		throw new InvalidSettingsException("please provide a Bayesian network to read");

        CheckUtils.checkSourceFile(filename);
        
    	return new BayesianNetworkPortSpec[] { new BayesianNetworkPortSpec() };
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

