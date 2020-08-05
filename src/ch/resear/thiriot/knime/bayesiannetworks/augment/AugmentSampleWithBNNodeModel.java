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

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.RowKey;
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
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;

import ch.resear.thiriot.knime.bayesiannetworks.DataTableToBNMapper;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.AbstractInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.BestInferenceEngine;
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
    		throw new IllegalArgumentException("The second input should be a data table", e);
    	}
    	final BufferedDataTable sample = sampleRead;
    	
    	// decode input Bayesian network
    	CategoricalBayesianNetwork bn = null;
    	try {
    		BayesianNetworkPortObject capsule = (BayesianNetworkPortObject)inObjects[1];
    		bn = capsule.getBN();
    	} catch (ClassCastException e) {
    		throw new IllegalArgumentException("The first input should be a Bayesian network", e);
    	}
    	
    	// no parameter to retrieve
    	
    	// define what we will add as columns
    	Map<NodeCategorical,DataTableToBNMapper> node2mapper = DataTableToBNMapper.createMapper(bn, logger);
    	
    	List<NodeCategorical> nodesForEvidence = new LinkedList<>();
    	List<NodeCategorical> nodesToAdd = new LinkedList<>();
    	Map<NodeCategorical,Integer> nodeEvidence2idx = new HashMap<>(); 
    	Map<NodeCategorical,Integer> nodeToAdd2idx = new HashMap<>(); 

    	for (NodeCategorical n: bn.enumerateNodes()) {
    		
    		if (sample.getDataTableSpec().containsName(n.getName())) {
    			nodesForEvidence.add(n);
    			nodeEvidence2idx.put(n, sample.getDataTableSpec().findColumnIndex(n.getName()));
    		} else {
    			nodesToAdd.add(n);
    			nodeToAdd2idx.put(n, sample.getDataTableSpec().getColumnNames().length + nodeToAdd2idx.size());
    		}
    	}
    	logger.info("will use columns "+nodesForEvidence+" as evidence in the Bayesian network");
    	logger.info("will create columns with variables "+nodesToAdd+" from the Bayesian network");
    	
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
        final AbstractInferenceEngine engine = new BestInferenceEngine(logger, rng, bn);
        
    	// iterate each row of data, and learn the count to later fill in the BN
    	Iterator<DataRow> itRows = sample.iterator();
    	
    	Set<NodeCategorical> failedNodes = new HashSet<NodeCategorical>();
    	
    	// TODO manage long!!!
    	int rowIdx = 0;
    	while (itRows.hasNext()) {
    	
    		DataRow row = itRows.next();
        	
    		// add evidence from the row
    		engine.clearEvidence();
    		for (NodeCategorical nodeForEvidence: nodeEvidence2idx.keySet()) {
    			DataCell val = row.getCell(nodeEvidence2idx.get(nodeForEvidence));
    			
    			engine.addEvidence(
    					nodeForEvidence, 
    					node2mapper.get(nodeForEvidence).getStringValueForCell(val)
    					);
    		}
    		//System.err.println("p(evidence): "+engine.getProbabilityEvidence());
    		
    		// copy the past results
    		DataCell[] results = new DataCell[row.getNumCells()+nodesToAdd.size()];
    		for (int j=0; j<row.getNumCells(); j++) {
    			results[j] = row.getCell(j);
    		}
    		
    		Map<NodeCategorical,String> generated = engine.sampleOne();

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
	        			new RowKey("Row " + rowIdx), 
	        			results
	        			)
        			);
    		

    		if (rowIdx % 100 == 0) { // TODO granularity?
	            // check if the execution monitor was canceled
	            exec.checkCanceled();
	            exec.setProgress(
	            		(double)rowIdx / sample.size(), 
	            		"augmenting row " + rowIdx);
        	}
    		rowIdx++;
    	}
    	
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
        // TODO Code executed on reset.
        // Models build during execute are cleared here.
        // Also data handled in load/saveInternals will be erased here.
    }

    @Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
	
    	// TODO 
        return new DataTableSpec[]{null};

        /*
    	try {
        	CategoricalBayesianNetwork bn = null;

    		BayesianNetworkPortObject capsule = (BayesianNetworkPortObject)inObjects[0];
    		bn = capsule.getBN();
    		
    		return new DataTableSpec[]{ new DataTableSpec(createSpecsForBN(bn)) };
    		
    	} catch (RuntimeException e) {
            return new DataTableSpec[]{null};
    	}
    	*/
        
    	 // TODO: check if user settings are available, fit to the incoming
        // table structure, and the incoming types are feasible for the node
        // to execute. If the node can execute in its current state return
        // the spec of its output data table(s) (if you can, otherwise an array
        // with null elements), or throw an exception with a useful user message

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

