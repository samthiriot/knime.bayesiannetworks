package ch.resear.thiriot.knime.bayesiannetworks.create.addnode;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;

import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortObject;


/**
 * This is the model implementation of AugmentSampleWithBNNode.
 * Adds additional columns based on the probabilities from a Bayesian network
 *
 * @author Samuel Thiriot
 */
public class AddNodeToBNNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(AddNodeToBNNodeModel.class);

    private final SettingsModelBoolean m_acceptMultiple = new SettingsModelBoolean("accept_multiple", false);
    
    
    /**
     * Constructor for the node model.
     */
    protected AddNodeToBNNodeModel() {
    
    	 super(
  	    		new PortType[] { BayesianNetworkPortObject.TYPE, BufferedDataTable.TYPE },
  	            new PortType[] { BayesianNetworkPortObject.TYPE });
   
    }

    @Override
	protected PortObject[] execute(
			PortObject[] inObjects, 
			ExecutionContext exec) throws Exception {

    	// decode parameter
    	final boolean accept_multiple = m_acceptMultiple.getBooleanValue();
    	
    	// decode input Bayesian network
    	CategoricalBayesianNetwork bn = null;
    	try {
    		BayesianNetworkPortObject capsule = (BayesianNetworkPortObject)inObjects[0];
    		bn = capsule.getBN();
    	} catch (ClassCastException e) {
    		throw new IllegalArgumentException("The first input should be a Bayesian network", e);
    	}
    	    	
    	// decode the input table 
    	BufferedDataTable sampleRead = null;
    	try {
    		sampleRead = (BufferedDataTable)inObjects[1];
    	} catch (ClassCastException e) {
    		throw new IllegalArgumentException("The second input should be a data table", e);
    	}
    	final BufferedDataTable sample = sampleRead;
    	
    	// TODO read parameters to know which columns to select!
    	
    	// for now we make it deterministic.
    	final int idxColDomain = 0;
    	DataColumnSpec specColVariable = sampleRead.getDataTableSpec().getColumnSpec(idxColDomain);
    	final int idxColProba = sampleRead.getDataTableSpec().getNumColumns()-1;
    	DataColumnSpec specColProba = sampleRead.getDataTableSpec().getColumnSpec(idxColProba);
    	List<DataColumnSpec> specColsDependant = IntStream.range(1, sampleRead.getDataTableSpec().getNumColumns()-1).mapToObj(i -> sample.getDataTableSpec().getColumnSpec(i)).collect(Collectors.toList());
    	logger.warn(
    			"will create a node p("+specColVariable.getName()+"|"+
    			specColsDependant.stream().map(s -> s.getName()).collect(Collectors.joining(","))+
    			")"
    			);
    	
    	// check the variable does not yet exists
        if (bn.getVariable(specColVariable.getName()) != null)
        	throw new InvalidSettingsException("a variable named "+specColVariable.getName()+" already exists in the network");
    	
        exec.setProgress(0, "creating the variable");
        
        // create the new variable (TODO ensure it does not exist already?)
        NodeCategorical newNode = new NodeCategorical(bn, specColVariable.getName());

        // ... find or create the parents
        Map<NodeCategorical,Integer> parentVariable2colIndex = new HashMap<>();
        for (DataColumnSpec dependantColSpec: specColsDependant) {
        	// build the domain we need in the parent
            List<String> domain = dependantColSpec.getDomain().getValues().stream().map(dc -> dc.toString()).collect(Collectors.toList());
        	// search for the parent in the BN
            NodeCategorical dependantVariable = bn.getVariable(dependantColSpec.getName()); 
            if (dependantVariable == null) {
            	// this variable does not exists (yet)
            	logger.warn("the Bayesian network does not contain the dependant variable p("+dependantColSpec.getName()+"); "+
            				"we create it with domain "+domain+" and equiprobability");
                dependantVariable = new NodeCategorical(bn, dependantColSpec.getName());
                dependantVariable.addDomain(domain);
                double[] probas = new double[domain.size()];
                Arrays.fill(probas, 1.0/domain.size());
                dependantVariable.setProbabilities(probas);
                bn.add(dependantVariable);
            } else {
            	// the variable exists already !
            	// ensure it contains all the variables we need?
            	Set<String> missingDomain = new HashSet<>(domain);
            	missingDomain.removeAll(dependantVariable.getDomain());
            	if (!missingDomain.isEmpty())
            		throw new InvalidSettingsException("the Bayesian network already contains the dependant variable "+dependantColSpec.getName()+", "+
            										"but it does not contains the necessary values "+missingDomain+" "+
            										"(its domain is: "+dependantVariable.getDomain()+")");
            }
            parentVariable2colIndex.put(dependantVariable, sampleRead.getDataTableSpec().findColumnIndex(dependantColSpec.getName()));
            newNode.addParent(dependantVariable);
        }
        
        // ... define the domain 
        List<String> domain = specColVariable.getDomain().getValues().stream().map(dc -> dc.toString()).collect(Collectors.toList());
        logger.warn("domain of p("+specColVariable.getName()+":"+domain);
        newNode.addDomain(domain);
        
        exec.setProgress(0, "building the Conditional Probability Table");
        
        // now iterate the table; 
    	Iterator<DataRow> itRows = sample.iterator();
    	int rowIdx = 0;
    	while (itRows.hasNext()) {
        	
		    // check if the execution monitor was canceled
            exec.checkCanceled();
            exec.setProgress(
            		(double)(rowIdx + 1) / sample.size());
        
            DataRow row = itRows.next();
        	
            // TODO what if missing???
            final String value = row.getCell(idxColDomain).toString();
            final Double probability = ((DoubleValue)row.getCell(idxColProba)).getDoubleValue();
            // read the pairs (parent, value) for this line
            Map<NodeCategorical,String> parent2Value = parentVariable2colIndex.entrySet().stream().collect(
            		Collectors.toMap(
            				k2v -> k2v.getKey(), 
            				k2v -> row.getCell(k2v.getValue()).toString()
            		));
            
            logger.warn("p("+specColVariable.getName()+"="+value+"|"+parent2Value+")="+probability);
            double pastProba = newNode.getProbability(value, parent2Value);
            if (!accept_multiple && (pastProba > 0))
            	throw new InvalidSettingsException("We already found a value for combination p("+
            								specColVariable.getName()+"="+value+"|"+
            								parent2Value.entrySet()
            											.stream()
            											.map(k2v -> k2v.getKey().getName()+"="+k2v.getValue())
            											.collect(Collectors.joining(","))+
            								")");
            newNode.setProbabilities(pastProba+probability, value, parent2Value);
            
    		rowIdx++;
    	}
    	
    	System.out.println("before normalization:\n"+newNode.toStringComplete());

    	// TODO are there missing values ????


    	// try to normalize
    	// TODO notice it is not always working!
    	newNode.normalize();
    	
        exec.setProgress(100, "done");

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
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
	
        return new DataTableSpec[]{null};

	}


    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {

    	m_acceptMultiple.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            

    	m_acceptMultiple.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            

    	m_acceptMultiple.validateSettings(settings);
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

