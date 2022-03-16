package ch.resear.thiriot.knime.bayesiannetworks.computeproba;

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
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
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
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;

import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;
import ch.resear.thiriot.knime.bayesiannetworks.DataTableToBNMapper;
import ch.resear.thiriot.knime.bayesiannetworks.LogIntoNodeLogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.EliminationInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.InferencePerformanceUtils;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortObject;


/**
 * @author Samuel Thiriot
 */
public class ComputeProbaNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(ComputeProbaNodeModel.class);
    private static final ILogger ilogger = new LogIntoNodeLogger(logger);
        
    // example value: the models count variable filled from the dialog 
    // and used in the models execution method. The default components of the
    // dialog work with "SettingsModels".
    private final SettingsModelString m_colname =
            new SettingsModelString("colname", "probability");
        
    
    /**
     * Constructor for the node model.
     */
    protected ComputeProbaNodeModel() {
        
        super(
	    		new PortType[] { BufferedDataTable.TYPE, BayesianNetworkPortObject.TYPE },
	            new PortType[] { BufferedDataTable.TYPE });
 
    }
    
    

    @Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
	
    	DataTableSpec tableSpecs = (DataTableSpec) inSpecs[0];
    	if (tableSpecs == null)
    		return new DataTableSpec[]{null};

    	return new DataTableSpec[] { createSpecsForTable(tableSpecs) };
	}
    
    private DataTableSpec createSpecsForTable(DataTableSpec tableSpecs) {

    	List<DataColumnSpec> specs = new LinkedList<DataColumnSpec>();
		
    	// add existing columns 
    	for (int i=0; i<tableSpecs.getNumColumns(); i++)
    		specs.add(tableSpecs.getColumnSpec(i));
    	
    	// add a column for the probability
    	specs.add(
    		new DataColumnSpecCreator(m_colname.getStringValue(), DoubleCell.TYPE).createSpec()
    	);
    	
    	
    	return new DataTableSpec(specs.toArray(new DataColumnSpec[specs.size()]));
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

    	// prepare mappings
    	exec.setMessage("preparing mappings");

    	// define what we will add as columns
    	Map<NodeCategorical,DataTableToBNMapper> node2mapper = DataTableToBNMapper.createMapper(bn, ilogger);
    	
    	List<NodeCategorical> nodesForEvidence = new LinkedList<>();
    	Map<NodeCategorical,Integer> nodeEvidence2idx = new HashMap<>(); 

    	for (NodeCategorical n: bn.enumerateNodes()) {
    		
    		if (sample.getDataTableSpec().containsName(n.getName())) {
    			nodesForEvidence.add(n);
    			nodeEvidence2idx.put(n, sample.getDataTableSpec().findColumnIndex(n.getName()));
    		} 
    	}

    	if (nodesForEvidence.isEmpty()){
    		throw new InvalidSettingsException("we found no column in the table matching the names of variable in the Bayesian network");
    	} else {
	    	logger.info("will use "+nodesForEvidence.size()+" columns from the KNIME table as evidence in the Bayesian network");
	    	for (NodeCategorical n: nodesForEvidence) {
	    		logger.info("\tthe column \""+n.name+"\" will be used as evidence for the variable "+n+" in the Bayesian network");	
	    	}
    	}
    	
    	exec.setMessage("preparing the output table");


    	// create specs
        DataTableSpec outputSpec = createSpecsForTable(sample.getDataTableSpec());
        BufferedDataContainer container = exec.createDataContainer(outputSpec);
        
        EliminationInferenceEngine engine = new EliminationInferenceEngine(
        		ilogger, 
        		null,//random, 
        		bn);

    	// iterate each row of data, and learn the count to later fill in the BN
    	Iterator<DataRow> itRows = sample.iterator();
    	
    	Set<NodeCategorical> failedNodes = new HashSet<NodeCategorical>();
    	
    	final long timestart = System.currentTimeMillis();
    	
    	InferencePerformanceUtils.singleton.reset();

    	// TODO manage long!!!
    	int rowIdx = 0;
    	while (itRows.hasNext()) {
    	
		    // check if the execution monitor was canceled
            exec.checkCanceled();
            exec.setProgress(
            		(double)(rowIdx + 1) / sample.size(), 
            		"computing row " + rowIdx);
        
            DataRow row = itRows.next();
        	
    		// add evidence from the row
    		for (NodeCategorical nodeForEvidence: nodeEvidence2idx.keySet()) {
    			DataCell val = row.getCell(nodeEvidence2idx.get(nodeForEvidence));
    			
    			// TODO deal with missing !!!
    			if (!val.isMissing())
    				engine.addEvidence(
    					nodeForEvidence, 
    					node2mapper.get(nodeForEvidence).getStringValueForCell(val)
    					);
    		}
    		engine.compute();
    		//System.err.println("p(evidence): "+engine.getProbabilityEvidence());
    		
    		// copy the existing row
    		DataCell[] results = new DataCell[row.getNumCells()+1];
    		for (int j=0; j<row.getNumCells(); j++) {
    			results[j] = row.getCell(j);
    		}
    		
    		// compute proba
    		double p = engine.getProbabilityEvidence();
    		engine.clearEvidence();
    		
    		results[row.getNumCells()] = DoubleCellFactory.create(p);
    				
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



    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
    	m_colname.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        m_colname.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        m_colname.validateSettings(settings);
        
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

