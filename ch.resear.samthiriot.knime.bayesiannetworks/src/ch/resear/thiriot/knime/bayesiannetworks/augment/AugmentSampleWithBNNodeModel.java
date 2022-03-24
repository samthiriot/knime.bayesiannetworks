package ch.resear.thiriot.knime.bayesiannetworks.augment;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.def.DefaultRow;
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
import org.knime.core.node.defaultnodesettings.SettingsModelSeed;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;

import cern.colt.Version;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;
import ch.resear.thiriot.knime.bayesiannetworks.DataTableToBNMapper;
import ch.resear.thiriot.knime.bayesiannetworks.LogIntoNodeLogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.AbstractInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.EliminationInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.InferencePerformanceUtils;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.RecursiveConditionningEngine;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortObject;


/**
 * This is the model implementation of AugmentSampleWithBNNode.
 * Adds additional columns based on the probabilities from a Bayesian network
 *
 * @author Samuel Thiriot
 */
public class AugmentSampleWithBNNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(AugmentSampleWithBNNodeModel.class);
    private static final ILogger ilogger = new LogIntoNodeLogger(logger);


    private final SettingsModelSeed m_seed = 
    		new SettingsModelSeed(
    				"seed", 
    				(int)System.currentTimeMillis(), 
    				false);

    
    /**
     * Constructor for the node model.
     */
    protected AugmentSampleWithBNNodeModel() {
    
    	 super(
  	    		// a BN as an input + a data table
  	    		new PortType[]{ BufferedDataTable.TYPE, BayesianNetworkPortObject.TYPE },
  	    		// one output of type 
  	            new PortType[] { BufferedDataTable.TYPE });
   
    }

    @Override
	protected PortObject[] execute(
			PortObject[] inObjects, 
			ExecutionContext exec) throws Exception {

    	// decode the input table 
    	BufferedDataTable sampleRead = null;
    	try {
    		sampleRead = (BufferedDataTable)inObjects[0];
    	} catch (ClassCastException e) {
    		throw new IllegalArgumentException("The first input should be a data table", e);
    	}
    	final BufferedDataTable sample = sampleRead;
    	
    	// decode input Bayesian network
    	CategoricalBayesianNetwork bn = null;
    	try {
    		BayesianNetworkPortObject capsule = (BayesianNetworkPortObject)inObjects[1];
    		bn = capsule.getBN();
    	} catch (ClassCastException e) {
    		throw new IllegalArgumentException("The second input should be a Bayesian network", e);
    	}
    	    	
        // retrieve the seed parameter
    	int seed; 
    	if (m_seed.getIsActive()) {
    		seed = (int)m_seed.getLongValue();
    		if ((long)seed != m_seed.getLongValue())
    			logger.info("the seed was converted from long "+m_seed.getLongValue()+" to int "+seed+"; this should have no impact for you");
    	} else 
    		seed = (int)System.currentTimeMillis();
    	
    	
    	exec.setMessage("preparing mappings");

    	// define what we will add as columns
    	Map<String,NodeCategorical> name2node = new HashMap<>();

    	Map<NodeCategorical,DataTableToBNMapper> node2mapper = DataTableToBNMapper.createMapper(bn, ilogger);
    	
    	// there are columns in the table which match nodes of the BN: we can use them for evidence (even if some will be missing)
    	List<NodeCategorical> nodesForEvidence = new LinkedList<>();
    	// there are nodes in the BN which do not match any column of the BN: we will add them as things to generate
    	List<NodeCategorical> nodesToAdd = new LinkedList<>();
    	Map<NodeCategorical,Integer> nodeEvidence2idx = new HashMap<>(); 
    	Map<Integer,NodeCategorical> idx2nodeEvidence = new HashMap<>(); 

    	Map<NodeCategorical,Integer> nodeToAdd2idx = new HashMap<>(); 

    	for (NodeCategorical n: bn.enumerateNodes()) {
    		
    		name2node.put(n.getName(), n);
    		
    		if (sample.getDataTableSpec().containsName(n.getName())) {
    			// this node matches a column with is already in the table
    			// is the domain of the table the same as the one of the node?
    			// TODO
    			nodesForEvidence.add(n);
    			// TODO factorize
    			nodeEvidence2idx.put(n, sample.getDataTableSpec().findColumnIndex(n.getName()));
    			idx2nodeEvidence.put(sample.getDataTableSpec().findColumnIndex(n.getName()), n);
    		} else {
    			nodesToAdd.add(n);
    			nodeToAdd2idx.put(n, sample.getDataTableSpec().getColumnNames().length + nodeToAdd2idx.size());
    		}
    	}
    	
    	// no quick leave: maybe there are missing values!
    	/*
    	if (nodesToAdd.isEmpty()) {
    		String w = "we found no variable in the Bayesian network which would miss in the table. The node will just return the input table.";
    		setWarningMessage(w);
    		logger.warn(w);
    		return new BufferedDataTable[]{sample};
    	}
    	*/

    	if (nodesForEvidence.isEmpty()){
    		logger.warn("we found no column in the table matching the names of variable in the Bayesian network. So the additional columns will be purely random, and independant of the columns of the input table.");
    		logger.warn("the Bayesian network contains as variable names: "+bn.getNodes().stream().map(n -> n.name).collect(Collectors.joining(",")));
    		setWarningMessage("no match between columns and variables. The additional columns are independant of the existing ones");
    	} else {
	    	logger.info("will use "+nodesForEvidence.size()+" columns from the KNIME table as evidence in the Bayesian network");
	    	for (NodeCategorical n: nodesForEvidence) {
	    		logger.info("\tthe column \""+n.name+"\" will be used as evidence for the variable "+n+" in the Bayesian network");	
	    	}
    	}
    	logger.info("will create "+nodesToAdd.size()+" columns to the table using values from the Bayesian network");
    	for (NodeCategorical n: nodesToAdd) {
    		logger.info("\tthe variable "+n+" will be used to add a column named \""+n.name+"\"");
    	}
    	
    	exec.setMessage("preparing the output table");

    	// create specs
    	DataColumnSpec[] columnSpecs = new DataColumnSpec[
    	                                  sample.getDataTableSpec().getColumnNames().length
    	                                  + nodesToAdd.size()];
    	int i;
    	for (i=0; i<sample.getDataTableSpec().getColumnNames().length; i++) {
    		columnSpecs[i] = sample.getDataTableSpec().getColumnSpec(i);
    	}
    	for (NodeCategorical n: nodesToAdd) {
    		columnSpecs[i++] = node2mapper.get(n).getSpecForNode();
    	}
        DataTableSpec outputSpec = new DataTableSpec(columnSpecs);
        BufferedDataContainer container = exec.createDataContainer(outputSpec);

    	// initialize a Bayesian inference engine
        exec.setMessage("init of the random engine");
    	logger.info("generating random numbers using the MersenneTwister pseudo-random number generator with seed "+seed+", as implemented in the COLT library "
        		+Version.getMajorVersion()+"."+Version.getMinorVersion()+"."+Version.getMicroVersion());
        
        final RandomEngine random = new MersenneTwister(seed);
        
        // TODO automatic selection?
        // TODO ?final AbstractInferenceEngine engine = new BestInferenceEngine(ilogger, random, bn);
        
        //EliminationInferenceEngine engine = new EliminationInferenceEngine(ilogger, random, bn); NO not good for reverse!
        AbstractInferenceEngine engine = new RecursiveConditionningEngine(ilogger, random, bn);
        
		
    	// iterate each row of data, and learn the count to later fill in the BN
    	Iterator<DataRow> itRows = sample.iterator();
        	
    	final long timestart = System.currentTimeMillis();
    	
    	InferencePerformanceUtils.singleton.reset();

    	long rowIdx = 0;
        long entitiesPerSecond = -1;
        
    	while (itRows.hasNext()) {
    	
		    // check if the execution monitor was canceled
            exec.checkCanceled();

            // from time to time, try to update the count of entities per sec
            if (rowIdx % 100 == 0)
            	try {
            		entitiesPerSecond = rowIdx / ( (System.currentTimeMillis() - timestart) / 1000);
            	} catch (java.lang.ArithmeticException e) { }
            
            if (entitiesPerSecond < 0)
	            exec.setProgress(
	            		(double)(rowIdx + 1) / sample.size(), 
	            		"augmenting row " + rowIdx);
            else
            	exec.setProgress(
	            		(double)(rowIdx + 1) / sample.size(), 
	            		"augmenting row " + rowIdx + " ("+entitiesPerSecond+"/s)");
            
            DataRow row = itRows.next();
        	
    		// add evidence from the row
    		for (NodeCategorical nodeForEvidence: nodeEvidence2idx.keySet()) {
    			DataCell val = row.getCell(nodeEvidence2idx.get(nodeForEvidence));
    			
    			if (!val.isMissing())
    				engine.addEvidence(
	    					nodeForEvidence, 
	    					node2mapper.get(nodeForEvidence).getStringValueForCell(val)
	    					);
    		}
    		try {
    			engine.compute();
        	} catch (java.lang.ArithmeticException e) { 
        		throw new RuntimeException("error when running the inference engine for row "+row.toString(), e);
        	}
			//System.err.println("p(evidence): "+engine.getProbabilityEvidence());
    	
    		Map<NodeCategorical,String> generated = null;
    		try {
    			generated = engine.sampleOne();
        	} catch (java.lang.ArithmeticException e) { 
        		throw new RuntimeException("error when sampling for row "+row.toString(), e);
        	}
    		engine.clearEvidence();
    		
    		// copy the past content of the table
    		DataCell[] results = new DataCell[row.getNumCells()+nodesToAdd.size()];
    		for (int j=0; j<row.getNumCells(); j++) {
    			
    			if (row.getCell(j).isMissing()) {
    				// the content is missing
    				
    				NodeCategorical n = idx2nodeEvidence.get(j);
    				if (n != null) {
    					// and also we have a corresponding node so we might complete it
    					results[j] = node2mapper.get(n).createCellForStringValue(generated.get(n));
    					// end of the processing here :-)
    					continue;
    				}
   
    			} 
    			
    			// copy the past result
    			results[j] = row.getCell(j);	    			
    		}
    		
    		// add the novel values
    		for (NodeCategorical nodeToAdd: nodeToAdd2idx.keySet()) {
    			int idxRes = nodeToAdd2idx.get(nodeToAdd);
    			DataCell data = node2mapper.get(nodeToAdd).createCellForStringValue(generated.get(nodeToAdd));
    			results[idxRes] = data;
    		}
    		
    		// add this has a result
    		// append
        	container.addRowToTable(
        			new DefaultRow(
        				row.getKey(), 
	        			results
	        			)
        			);
    		
    		rowIdx++;
    	}
    	
    	final long timeend = System.currentTimeMillis();
    	final long durationms = (timeend - timestart);
    	logger.info("inference took "+(durationms/sample.size())+"ms per line");
    	
    	InferencePerformanceUtils.singleton.display(ilogger);
    	
        // once we are done, we close the container and return its table
        exec.setProgress(100, "closing the output table");
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

    @Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
	
        return new DataTableSpec[]{null};

	}


    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {

    	m_seed.saveSettingsTo(settings);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
    	m_seed.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
    	m_seed.validateSettings(settings);
    	
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

