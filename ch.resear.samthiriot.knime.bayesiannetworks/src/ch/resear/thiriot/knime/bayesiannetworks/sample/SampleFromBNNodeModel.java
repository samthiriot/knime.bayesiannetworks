package ch.resear.thiriot.knime.bayesiannetworks.sample;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
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
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
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
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.InferencePerformanceUtils;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.RecursiveConditionningEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.SimpleConditionningInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortObject;


/**
 * This is the model implementation of SampleFromBNNode.
 * Using a Bayesian network which describes densities of probabilities, this node generates a population of entities (data table) with columns corresponding the variables of the BN.
 *
 * @author Samuel Thiriot
 */
public class SampleFromBNNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(SampleFromBNNodeModel.class);
    private static final ILogger ilogger = new LogIntoNodeLogger(logger);
        
    private static final int MIN_ROWS_FOR_PARALLEL = 19;
    
    /** the settings key which is used to retrieve and 
        store the settings (from the dialog or from a settings file)    
       (package visibility to be usable from the dialog). */
	static final String CFGKEY_COUNT = "Count";

    /** initial default count value. */
    static final int DEFAULT_COUNT = 100;

    // example value: the models count variable filled from the dialog 
    // and used in the models execution method. The default components of the
    // dialog work with "SettingsModels".
    private final SettingsModelIntegerBounded m_count =
        new SettingsModelIntegerBounded(SampleFromBNNodeModel.CFGKEY_COUNT,
                    SampleFromBNNodeModel.DEFAULT_COUNT,
                    0, Integer.MAX_VALUE);
    
    private final SettingsModelSeed m_seed = 
    		new SettingsModelSeed(
    				"seed", 
    				(int)System.currentTimeMillis(), 
    				false);
    
    private final SettingsModelBoolean m_threadsAuto = new SettingsModelBoolean(
    		"m_threads_auto", 
    		true);
    private final SettingsModelIntegerBounded m_threads = new SettingsModelIntegerBounded(
    		"m_threads", 
    		Runtime.getRuntime().availableProcessors(), 1, 128);

    private Map<NodeCategorical,DataTableToBNMapper> node2mapper = new HashMap<>();
    
    /**
     * Constructor for the node model.
     */
    protected SampleFromBNNodeModel() {
        
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
    			
    	for (NodeCategorical node: bn.enumerateNodes()) {
    		
    		specs.add(node2mapper.get(node).getSpecForNode());
    	}
    	
    	return specs.toArray(new DataColumnSpec[specs.size()]);
    }
    

    @Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
	
        return new DataTableSpec[]{null};

	}
    
    private long totalRowsGenerated = 0;
    
    private class BNToTableSampler implements Callable<BufferedDataTable> {

    	private final RandomEngine random;
    	private final AbstractInferenceEngine engine;
    	private final DataTableSpec outputSpec;
    	private final ExecutionContext exec;
    	private final int countToSample;
    	private final CategoricalBayesianNetwork bn;
        private final int firstId;
        
    	public BNToTableSampler(
    				RandomEngine _random, 
    				CategoricalBayesianNetwork bn,
    				DataTableSpec outputSpec,
    				ExecutionContext exec,
    				int countToSample,
    				int firstId) {

    		this.outputSpec = outputSpec;
    		this.exec = exec;
    		this.countToSample = countToSample;
    		this.bn = bn;
    		this.firstId = firstId;
    		
    		this.random = new MersenneTwister(_random.nextInt());
            
    		this.engine = new SimpleConditionningInferenceEngine(ilogger, _random, bn);
    		
    		/*
    		this.engine = new SimpleConditionningInferenceEngine(
            		ilogger, 
            		random,
            		bn);*/
    	}
    	
		@Override
		public BufferedDataTable call() throws Exception {

	        BufferedDataContainer container = exec.createDataContainer(outputSpec);
	        for (int i=0; i<countToSample; i++) {
	            
	        	totalRowsGenerated++;
	        	
	        	if (totalRowsGenerated%7==0)
	        		exec.setMessage("row "+totalRowsGenerated);
	        	
	        	exec.setProgress(
	            		((double)i+1.0) / countToSample//, 
	            		//"Adding row " + i
	            		);
	            
	        	// TODO draw several individuals a time ?
	        	
	        	// sample one individual
	        	Map<NodeCategorical,String> variable2value = engine.sampleOne();
	        	
	        	// convert to KNIME cells
	        	DataCell[] results = new DataCell[variable2value.size()];
	        	int j=0;
	        	for (NodeCategorical node : bn.enumerateNodes()) {
	        		
	        		String valueStr = variable2value.get(node);
	        		
	        		results[j++] = node2mapper.get(node).createCellForStringValue(valueStr);
	        		
	        	}
	        	
	        	// append
	        	container.addRowToTable(
	        			new DefaultRow(
		        			new RowKey("Row " + (i+firstId)), 
		        			results
		        			)
	        			);
	        	
	        	exec.checkCanceled();
	        	
	        }
	    	
	        // once we are done, we close the container and return its table
	        container.close();
	        
			return container.getTable();
		}
    	
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
    	
    	// retrieve parameter
    	final int countToSample = m_count.getIntValue();
        
        // retrieve the seed
    	int seed; 
    	if (m_seed.getIsActive()) {
    		seed = (int)m_seed.getLongValue();
    		if ((long)seed != m_seed.getLongValue())
    			logger.info("the seed was converted from long "+m_seed.getLongValue()+" to int "+seed+"; this should have no impact for you");
    	} else 
    		seed = (int)System.currentTimeMillis();
    	
    	exec.setMessage("preparing the output table");
    	
    	// create output container
    	DataColumnSpec[] columnSpecs = createSpecsForBN(bn);
        DataTableSpec outputSpec = new DataTableSpec(columnSpecs);
    			

        exec.setMessage("init of the random engine");
    	logger.info("generating random numbers using the MersenneTwister pseudo-random number generator with seed "+seed+", as implemented in the COLT library "
        		+Version.getMajorVersion()+"."+Version.getMinorVersion()+"."+Version.getMicroVersion());
        final RandomEngine random = new MersenneTwister(seed);
        exec.checkCanceled();

        exec.setMessage("preparation of the inference engine");
    	logger.info("using the Simple Conditioning Inference Engine");
        exec.checkCanceled();
        
        // prepare parallel processing
        int threadsToUse = 1;
        {
	        int threadsMax = m_threads.getIntValue(); 
	    	if (m_threadsAuto.getBooleanValue())
	    		threadsMax = Runtime.getRuntime().availableProcessors();
	    	
	    	while ( (threadsToUse < threadsMax) && (countToSample / threadsToUse > MIN_ROWS_FOR_PARALLEL) ) {
	    		threadsToUse++;
	    	}
	    	logger.debug("will use "+threadsToUse+" threads to generate the data");
        }
    	
        exec.setProgress(0, "generating rows");
    	InferencePerformanceUtils.singleton.reset();
    	
    	// submit the execution of the threads
        exec.setMessage("sampling");
        List<BNToTableSampler> samplers = new ArrayList<SampleFromBNNodeModel.BNToTableSampler>(threadsToUse);
    	{
    		int countRemaining = countToSample;
    		int countDistributed = 0;
	        for (int t=0; t<threadsToUse-1; t++) {
	        	int count = countToSample/threadsToUse;
	        	countRemaining -= count;
	        	samplers.add(
	        			new BNToTableSampler(
	        					random, bn, outputSpec, 
	        					exec.createSubExecutionContext(0.9/threadsToUse), 
	        					count,
	        					countDistributed
	        			));
	        	countDistributed += count;
	        }
        	samplers.add(
	        		new BNToTableSampler(
	    					random, bn, outputSpec, 
	    					exec.createSubExecutionContext(0.9/threadsToUse), 
	    					countRemaining,
	    					countDistributed
	    			));

    	}
        exec.checkCanceled();

        // submit executions
    	ExecutorService executorService = Executors.newFixedThreadPool(threadsToUse);
    	totalRowsGenerated = 0;
    	List<Future<BufferedDataTable>> results = executorService.invokeAll(samplers);
        
        exec.checkCanceled();
        
        // merge
        exec.setProgress("merging tables");
        BufferedDataTable resTable;
        {
	        final BufferedDataTable[] resultTables = new BufferedDataTable[threadsToUse];
	        for (int i = 0; i < resultTables.length; i++) {
	            resultTables[i] = results.get(i).get();
	        }
	        resTable = exec.createConcatenateTable(
        			exec.createSubProgress(0.1), 
        			resultTables
        			);
        }
        
        pushFlowVariableInt("sampled_count", countToSample);

        //exec.setProgress(100, "closing outputs");

        return new BufferedDataTable[]{ resTable };
        
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

        // TODO save user settings to the config object.
        
        m_count.saveSettingsTo(settings);
        m_seed.saveSettingsTo(settings);
        
        m_threads.saveSettingsTo(settings);
        m_threadsAuto.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        m_count.loadSettingsFrom(settings);
        m_seed.loadSettingsFrom(settings);
        
        m_threads.loadSettingsFrom(settings);
        m_threadsAuto.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        m_count.validateSettings(settings);
        m_seed.validateSettings(settings);
        
        m_threads.validateSettings(settings);
        m_threadsAuto.validateSettings(settings);
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

