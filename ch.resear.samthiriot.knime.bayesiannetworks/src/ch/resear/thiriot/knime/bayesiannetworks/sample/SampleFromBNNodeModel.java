package ch.resear.thiriot.knime.bayesiannetworks.sample;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.IntCell.IntCellFactory;
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
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;

import cern.colt.Version;
import cern.jet.random.Binomial;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;
import ch.resear.thiriot.knime.bayesiannetworks.DataTableToBNMapper;
import ch.resear.thiriot.knime.bayesiannetworks.LogIntoNodeLogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.lib.inference.SimpleConditionningInferenceEngine;
import ch.resear.thiriot.knime.bayesiannetworks.lib.sampling.EntitiesAndCount;
import ch.resear.thiriot.knime.bayesiannetworks.lib.sampling.ForwardSamplingIterator;
import ch.resear.thiriot.knime.bayesiannetworks.lib.sampling.MultinomialRecursiveSamplingIterator;
import ch.resear.thiriot.knime.bayesiannetworks.lib.sampling.RoundAndSampleRecursiveSamplingIterator;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortObject;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortSpec;


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
    
    private final SettingsModelBoolean m_groupRows = new SettingsModelBoolean("m_grouprows", true);
    
    private final SettingsModelString m_generationMethod = new SettingsModelString(
    				"m_generation_method", MultinomialRecursiveSamplingIterator.GENERATION_METHOD_NAME);
    
    private final SettingsModelBoolean m_threadsAuto = new SettingsModelBoolean(
    		"m_threads_auto", 
    		true);
    private final SettingsModelIntegerBounded m_threads = new SettingsModelIntegerBounded(
    		"m_threads", 
    		Runtime.getRuntime().availableProcessors(), 1, 128);

    private final SettingsModelBoolean m_noStorage = new SettingsModelBoolean("m_nostorage", false);

    
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
    			
    	for (NodeCategorical node: bn.getNodesSortedByName()) {
    		
    		specs.add(node2mapper.get(node).getSpecForNode());
    	}
    	
    	if (m_groupRows.getBooleanValue() && 
    			!m_generationMethod.getStringValue().equals(ForwardSamplingIterator.GENERATION_METHOD_NAME))
    		specs.add(new DataColumnSpecCreator("count", IntCell.TYPE).createSpec());
    	
    	return specs.toArray(new DataColumnSpec[specs.size()]);
    }
    

    @Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
    	
    	BayesianNetworkPortSpec specBN = (BayesianNetworkPortSpec) inSpecs[0];
    	
    	if (specBN != null) {
    		
        	Map<String,DataTableToBNMapper> node2mapper = DataTableToBNMapper.createMapper(specBN, ilogger);

        	List<DataColumnSpec> specs = new LinkedList<DataColumnSpec>();
        	for (String nodeName: specBN.getSortedVariableNames()) {
        		
        		specs.add(node2mapper.get(nodeName).getSpecForNode());
        	}
        	
        	if (m_groupRows.getBooleanValue() && 
        			!m_generationMethod.getStringValue().equals(ForwardSamplingIterator.GENERATION_METHOD_NAME))
        		specs.add(new DataColumnSpecCreator("count", IntCell.TYPE).createSpec());
        	
        	DataColumnSpec[] specsArray = specs.toArray(new DataColumnSpec[specs.size()]);
        	
    		return new DataTableSpec[]{ new DataTableSpec(specsArray) };

    	} else 
    		return new DataTableSpec[]{null};

	}
    
    private long totalRowsGenerated = 0;
    private long timestampStart = 0;
    private boolean groupRows = false;
    
    private class BNToTableSampler implements Callable<BufferedDataTable> {

    	private final DataTableSpec outputSpec;
    	private final ExecutionContext exec;
    	private final int countToSample;
    	private final CategoricalBayesianNetwork bn;
        private final int firstId;
        private final RandomEngine random;
        private final String method;
        private final boolean nostorage;
        
    	public BNToTableSampler(
    				RandomEngine random, 
    				CategoricalBayesianNetwork bn,
    				DataTableSpec outputSpec,
    				ExecutionContext exec,
    				int countToSample,
    				int firstId,
    				final String method,
    				boolean nostorage) {

    		this.outputSpec = outputSpec;
    		this.exec = exec;
    		this.countToSample = countToSample;
    		this.bn = bn; //.clone();
    		this.firstId = firstId;
    		this.random = new MersenneTwister(random.nextInt());
    		this.method = method;
    		this.nostorage = nostorage;
    	}
    	
		@Override
		public BufferedDataTable call() throws Exception {

	        BufferedDataContainer container = exec.createDataContainer(outputSpec);

	        Iterator<EntitiesAndCount> it;
	        if (method.equals(RoundAndSampleRecursiveSamplingIterator.GENERATION_METHOD_NAME))
		        it = new RoundAndSampleRecursiveSamplingIterator(
		        		countToSample, 
		        		bn, 
		        		random, 
		        		new SimpleConditionningInferenceEngine(ilogger, null, bn),
		        		exec, 
		        		ilogger);
	        else if (method.equals(MultinomialRecursiveSamplingIterator.GENERATION_METHOD_NAME))
	        	it = new MultinomialRecursiveSamplingIterator(
		        		countToSample, 
		        		bn, 
		        		new Binomial(42, 0.1, random), 
		        		new SimpleConditionningInferenceEngine(ilogger, null, bn),
		        		exec, 
		        		ilogger);
	        else if (method.equals(ForwardSamplingIterator.GENERATION_METHOD_NAME))
	        	it = new ForwardSamplingIterator(random, bn, countToSample, ilogger);
	        else
	        	throw new RuntimeException("Unknown generation method "+method);
	        
	        int done = 0;
	        int rows = 0;
	        while (it.hasNext()) {
	        	double progress = (double)done/countToSample;
	        	long timestampNow = System.currentTimeMillis();
	        	if (firstId == 0) { // only the first thread makes message updates
		        	String msg = "entity "+done;

		        	long elapsedSeconds = (timestampNow - timestampStart)/1000;
		        	if (elapsedSeconds > 10) {
			        	double entitiesPerSecond = totalRowsGenerated / elapsedSeconds;
			        	//logger.warn("generating "+((int)entitiesPerSecond)+" rows per second");
			        	msg = msg + " ("+(int)entitiesPerSecond+"/s)";
		        	}
		        	exec.setProgress(progress, msg);
	        	} else {
	        		exec.setProgress(progress);
	        	}
	        	
	        	EntitiesAndCount next;
	        	try {
		        	next = it.next();
		        	done += next.count;
		        	if (next.node2value.isEmpty())
		        		throw new RuntimeException("no entity generated...");
		        	totalRowsGenerated += next.count;
	        	} catch (RuntimeException e) {
	        		e.printStackTrace();
	        		throw new RuntimeException("Error when sampling the next entity: "+e.getMessage(), e);
	        	}
	        	//System.out.println(next);
	        	
	        	if (!nostorage) {
		        	if (groupRows) {
			        	// convert to KNIME cells
			        	DataCell[] results = new DataCell[next.node2value.size()+1];
			        	int j=0;
			        	for (NodeCategorical node : bn.getNodesSortedByName()) {
			        		String valueStr = next.node2value.get(node);
			        		if (valueStr == null)
			        			throw new RuntimeException("value for node "+node+" not found");
			        		results[j++] = node2mapper.get(node).createCellForStringValue(valueStr);
			        	}
			        	results[j] = IntCellFactory.create(next.count);
			        	
			        	// append
			        	container.addRowToTable(
			        			new DefaultRow(
				        			new RowKey("Row " + (firstId + rows++) ), 
				        			results
				        			)
			        			);
		        	} else {
		        		// convert to KNIME cells
			        	DataCell[] results = new DataCell[next.node2value.size()];
			        	int j=0;
			        	for (NodeCategorical node : bn.getNodesSortedByName()) {
			        		String valueStr = next.node2value.get(node);
			        		if (valueStr == null)
			        			throw new RuntimeException("value for node "+node+" not found");
			        		results[j++] = node2mapper.get(node).createCellForStringValue(valueStr);
			        	}
			        	for (int i=0; i<next.count; i++) {
				        	// append
				        	container.addRowToTable(
				        			new DefaultRow(
					        			new RowKey("Row " + (firstId + rows++) ), 
					        			results
					        			)
				        			);
			        	}
		        	}
	        	}
	        	exec.checkCanceled();
	        }
	        
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
    	
    	// retrieve parameters
    	final int countToSample = m_count.getIntValue();
    	final String generationMethod = m_generationMethod.getStringValue();
    	groupRows = m_groupRows.getBooleanValue() && !generationMethod.equals(ForwardSamplingIterator.GENERATION_METHOD_NAME);
    	
    	final boolean nostorage = m_noStorage.getBooleanValue();
    	
    	if (nostorage)
    		setWarningMessage("storage is disabled; no data will be produced");
    	
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
	        					countDistributed,
	        					generationMethod,
	        					nostorage
	        			));
	        	countDistributed += count;
	        }
        	samplers.add(
	        		new BNToTableSampler(
	    					random, bn, outputSpec, 
	    					exec.createSubExecutionContext(0.9/threadsToUse), 
	    					countRemaining,
	    					countDistributed,
	    					generationMethod, 
	    					nostorage
	    			));

    	}
        exec.checkCanceled();

        // submit executions
    	ExecutorService executorService = Executors.newFixedThreadPool(threadsToUse);
    	totalRowsGenerated = 0;
    	timestampStart = System.currentTimeMillis();
    	List<Future<BufferedDataTable>> results = executorService.invokeAll(samplers);
    	int performance;
    	{
    		long timestampNow = System.currentTimeMillis();
    		long elapsedMilliSeconds = (timestampNow - timestampStart);
    		performance = (int)(((double)countToSample/(double)elapsedMilliSeconds)*1000.0);
    		logger.info(
    				"generation of "+countToSample+" entities on "+threadsToUse+" CPUs with method "+generationMethod
    						+ " took "+elapsedMilliSeconds+"s, that is on average "+performance+" entities/s");
    	}
    	executorService.shutdown();
    	
        // merge
        BufferedDataTable resTable;
        if (threadsToUse > 1) {
            exec.setProgress("merging tables");
	        final BufferedDataTable[] resultTables = new BufferedDataTable[threadsToUse];
	        for (int i = 0; i < resultTables.length; i++) {
	            resultTables[i] = results.get(i).get();
	        }
	        resTable = exec.createConcatenateTable(
        			exec.createSubProgress(0.1), 
        			resultTables
        			);
        } else {
        	resTable = results.get(0).get();
        }
        
        pushFlowVariableInt("sampled_count", countToSample);
        pushFlowVariableInt("sampling_performance_entities_per_second", performance);

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
        
        m_count.saveSettingsTo(settings);
        m_seed.saveSettingsTo(settings);
        
        m_generationMethod.saveSettingsTo(settings);
        
        m_threads.saveSettingsTo(settings);
        m_threadsAuto.saveSettingsTo(settings);
        
        m_groupRows.saveSettingsTo(settings);
        
        m_noStorage.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        m_count.loadSettingsFrom(settings);
        m_seed.loadSettingsFrom(settings);
        
        m_generationMethod.loadSettingsFrom(settings);
        
        m_threads.loadSettingsFrom(settings);
        m_threadsAuto.loadSettingsFrom(settings);
        
        m_groupRows.loadSettingsFrom(settings);
        
        m_noStorage.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        m_count.validateSettings(settings);
        m_seed.validateSettings(settings);
        
        m_generationMethod.validateSettings(settings);
        
        m_threads.validateSettings(settings);
        m_threadsAuto.validateSettings(settings);
        
        m_groupRows.validateSettings(settings);
        
        m_noStorage.validateSettings(settings);
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

