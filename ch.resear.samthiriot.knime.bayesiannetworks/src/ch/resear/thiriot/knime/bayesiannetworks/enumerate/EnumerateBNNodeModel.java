package ch.resear.thiriot.knime.bayesiannetworks.enumerate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.DoubleCell.DoubleCellFactory;
import org.knime.core.node.BufferedDataContainer;
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

import ch.resear.thiriot.knime.bayesiannetworks.DataTableToBNMapper;
import ch.resear.thiriot.knime.bayesiannetworks.LogIntoNodeLogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.IteratorCategoricalVariables;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortObject;


/**
 * This is the model implementation of SampleFromBNNode.
 * Using a Bayesian network which describes densities of probabilities, this node generates a population of entities (data table) with columns corresponding the variables of the BN.
 *
 * @author Samuel Thiriot
 */
public class EnumerateBNNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(EnumerateBNNodeModel.class);
    private static final ILogger ilogger = new LogIntoNodeLogger(logger);
        
    private final SettingsModelBoolean m_skipNull = new SettingsModelBoolean("skip_null", true);

    private Map<NodeCategorical,DataTableToBNMapper> node2mapper = new HashMap<>();
    
    /**
     * Constructor for the node model.
     */
    protected EnumerateBNNodeModel() {
        
        super(
	    		// a BN as an input
	    		new PortType[] { BayesianNetworkPortObject.TYPE },
	    		// one output of type 
	            new PortType[] { BufferedDataTable.TYPE });
 
    }
    
    
    protected DataColumnSpec[] createSpecsForBN(CategoricalBayesianNetwork bn) {
    	
    	node2mapper.clear();
    	
    	node2mapper.putAll(DataTableToBNMapper.createMapper(bn, ilogger));
    	
    	
    	List<DataColumnSpec> specs = new LinkedList<DataColumnSpec>();
    		
    	// add columns for all the variables
    	for (NodeCategorical node: bn.enumerateNodes()) {
    		
    		specs.add(node2mapper.get(node).getSpecForNode());
    	}
    	
    	// add a column for the probability
    	specs.add(
    		new DataColumnSpecCreator("probability", DoubleCell.TYPE).createSpec()
    	);
    	
    	return specs.toArray(new DataColumnSpec[specs.size()]);
    }
    

    @Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
	
        return new DataTableSpec[]{null};

	}
    
    protected double computeProbability(
    		CategoricalBayesianNetwork bn, 
    		Map<NodeCategorical, String> variable2value) {
    	
    	double p = 1.0;
    	
    	for (NodeCategorical node: bn.enumerateNodes()) {
    		//System.out.println(node);
    		
    		// create the list key/value for only the parents
    		Map<NodeCategorical,String> parent2value = new HashMap<NodeCategorical, String>(variable2value);
    		parent2value.keySet().retainAll(node.getParents());
    		
    		String value = variable2value.get(node);
    		double pp = node.getProbability(value, parent2value);
    		
    		//System.out.println("p "+node+" => "+pp);
    		
    		if (pp == 0) {
    			// if there is an impossibility, it will propagate!
    			return 0;
    		}
    			
    		p = p * pp;
    	}
    	
    	return p;
    	
    }
    
    @Override
	protected PortObject[] execute(
			PortObject[] inObjects, 
			ExecutionContext exec) throws Exception {
		
    	// decode input Bayesian network
    	if (inObjects.length == 0) {
    		throw new IllegalArgumentException("No Bayesian network found as input");
    	}
    	if (inObjects.length > 1) {
    		throw new IllegalArgumentException("Only one Bayesian network expected as input");
    	}
    	
    	CategoricalBayesianNetwork bn = null;
    	try {
    		BayesianNetworkPortObject capsule = (BayesianNetworkPortObject)inObjects[0];
    		bn = capsule.getBN();
    	} catch (ClassCastException e) {
    		throw new IllegalArgumentException("The input should be a Bayesian network", e);
    	}
    	
    	final boolean skipNull = m_skipNull.getBooleanValue();

    	exec.setMessage("preparing the output table");
    	
    	// create output container
    	DataColumnSpec[] columnSpecs = createSpecsForBN(bn);
        DataTableSpec outputSpec = new DataTableSpec(columnSpecs);
    			
        BufferedDataContainer container = exec.createDataContainer(outputSpec);
        exec.checkCanceled();

        exec.setProgress(0, "generating rows");

    	IteratorCategoricalVariables it = bn.iterateDomains();
    	
    	
        // simple conditioning is the best engine when it comes to sample without evidence
        //final RandomEngine random = new MersenneTwister();
        //final AbstractInferenceEngine engine = new EliminationInferenceEngine(ilogger, random, bn);
        
        // RecursiveConditionningEngine
        
        
        /*
        		new SimpleConditionningInferenceEngine(
        		ilogger, 
        		random,
        		bn);
        */
    	
    	// compute the maximum combination feasible 
    	long total = 1;
        for (NodeCategorical node : bn.enumerateNodes())
        	total += total * node.getDomainSize();
        System.out.println("total expected "+total);

    	long i=0;
    	while (it.hasNext()) {
    		
    		i++;
        	exec.setProgress(
            		((double)i) / total, 
            		"Exploring combination " + i);
    		exec.checkCanceled();

    		Map<NodeCategorical, String> variable2value = it.next();
    		//System.out.println(variable2value);
    		
        	//System.out.println("computing the joint probability");
        	//double p = bn.jointProbabilityFromFactors(variable2value);
        	
        	double p = computeProbability(bn, variable2value);
        	
        	if (skipNull && p==0)
        		continue;
        	
    		// convert to KNIME cells
        	DataCell[] results = new DataCell[variable2value.size()+1];
        	int j=0;
        	for (NodeCategorical node : bn.enumerateNodes()) {
        		
        		String valueStr = variable2value.get(node);
        		
        		results[j++] = node2mapper.get(node).createCellForStringValue(valueStr);
        		
        	}
        	
        	/*
        	engine.addEvidence(variable2value);
        	double p = engine.getProbabilityEvidence();
        	engine.clearEvidence();
        	*/
        	//System.out.println("p = "+p);
        	results[j] = DoubleCellFactory.create(p);
    		
        	// append
        	container.addRowToTable(
        			new DefaultRow(
	        			new RowKey("Row " + i), 
	        			results
	        			)
        			);
        	
    	}
    	
        exec.setProgress(100, "closing outputs");

        // once we are done, we close the container and return its table
        container.close();
        BufferedDataTable out = container.getTable();
        return new BufferedDataTable[]{out};
        
	}

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        
    	// nothing to do
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
    	
    	m_skipNull.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    	
    	m_skipNull.loadSettingsFrom(settings); 
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    	
    	m_skipNull.validateSettings(settings);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        
    	// nothing to do
    	
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
    	
    	// nothing to do .

    }

}

