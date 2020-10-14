package ch.resear.thiriot.knime.bayesiannetworks.learn;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
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
import org.knime.core.node.defaultnodesettings.SettingsModelColumnName;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
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
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortSpec;


/**
 * This is the model implementation of LearnBNFromSample.
 * For a given Bayesian network, learns the conditional probabilities from a given sample
 *
 * @author Samuel Thiriot
 */
public class LearnBNFromSampleNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(LearnBNFromSampleNodeModel.class);
    private static final ILogger ilogger = new LogIntoNodeLogger(logger);

    public static final String METHOD_NOCASE_EQUIPROBABILITY = "assume equiprobability";
    public static final String METHOD_NOCASE_PREVIOUS = "keep previous probabilities";
    
    private SettingsModelIntegerBounded m_constant = new SettingsModelIntegerBounded(
    		"m_addconstant", 0, 
    		0, 1000);
    
    private SettingsModelBoolean m_useWeightColumn = new SettingsModelBoolean(
    		"m_use_weight_colum", false);
    
    private SettingsModelColumnName m_colnameWeight = new SettingsModelColumnName("m_colname", null);
    
    private SettingsModelString m_methodNoCase = new SettingsModelString(
    		"m_method_no_vase", 
    		LearnBNFromSampleNodeModel.METHOD_NOCASE_PREVIOUS);
    
    /**
     * Constructor for the node model.
     */
    protected LearnBNFromSampleNodeModel() {
    
    	 super(
 	    		// a BN as an input + a data table
 	    		new PortType[]{ BayesianNetworkPortObject.TYPE, BufferedDataTable.TYPE },
 	    		// one output of type 
 	            new PortType[] { BayesianNetworkPortObject.TYPE });
  
    }


    @Override
	protected PortObject[] execute(
			PortObject[] inObjects, 
			ExecutionContext exec) throws Exception {
		
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
    	
    	
    	// retrieve parameters
    	final int constantToAdd = m_constant.getIntValue();
    	final boolean useWeightColumn = m_useWeightColumn.getBooleanValue();
    	final String nameWeightColumn = m_colnameWeight.getColumnName();
    	final int idxWeightColumn = sample.getDataTableSpec().findColumnIndex(nameWeightColumn);
    	final String methodNoCase = m_methodNoCase.getStringValue();
    	final boolean methodNoCaseEquiproba = METHOD_NOCASE_EQUIPROBABILITY.equals(methodNoCase);
    	
    	// the future result
    	CategoricalBayesianNetwork learnt = bn.clone();

    	// TODO make it quicker  
    	

    	// identify the set of columns we are interested in,
    	// that is the columns for which we do have a node
    	Set<String> allColumnsOfInterest = new HashSet<>();
    	for (NodeCategorical n: learnt.getNodes()) {
    	
    		if (sample.getDataTableSpec().containsName(n.getName())) {
    			allColumnsOfInterest.add(n.getName());
    		}
    	}
    	
    	// identify the groups of columns of interest, 
    	// that is the list of the combinations of columns we can 
    	// and will study
    	// create...
    	// ... for each column name, the node which is interested by the result
    	Set<NodeCategorical> nodesToLearn = new HashSet<>();
    	
    	for (NodeCategorical n: learnt.getNodes()) {
        	
    		if (!sample.getDataTableSpec().containsName(n.getName())
    				|| n.getAllAncestors().stream()
    						.map( node -> node.getName())
    						.anyMatch( s -> !sample.getDataTableSpec().containsName(s)) ) {
    			logger.warn("will not learn the node "+n.getName()+" for which columns are not available");
    			continue;
    		} 

    		logger.info("will learn the node "+n.getName());
    		nodesToLearn.add(n);
    		
    		
    	}    	
    	
    	// count how many entries we have
    	// at the end we need
    	// Node "rain" = "true" | weather = nice, temperature = low  =>  10
    	// Node "rain" = "true" | weather = ugly, temperature = low  =>  100
    	// Node "rain" = "true" | weather = nice, temperature = high =>  20
    	// Node "rain" = "true" | weather = ugly, temperature = high  =>  200

    	// Node "weather" = "nice" |  => 100
    	// Node "weather" = "ugly" |  => 500
    
    	exec.setMessage("initializing counters");
    	// prepare the empty data structure
    	Map<NodeCategorical,
    		Map<String,
    			Map<Map<NodeCategorical,
    					String>,
    					Double>>> node2value2coordinates2count = new HashMap<>();
    	
    	for (NodeCategorical n: nodesToLearn) {
    		
    		Map<String,Map<Map<NodeCategorical,String>,Double>> value2coordinates2count = new HashMap<>();
    		
    		for (String value : n.getDomain()) {
    			//String value = valueRaw.toLowerCase();
    			//System.out.println("studying "+n.getName()+"="+value);
    			Map<Map<NodeCategorical,String>,Double> coordinates2count = new HashMap<>();
    			IteratorCategoricalVariables itDomains = learnt.iterateDomains(n.getParents());
    			while (itDomains.hasNext()) {
    				Map<NodeCategorical,String> coord = itDomains.next().entrySet().stream().collect(
    						Collectors.toMap(
    								entry -> entry.getKey(), 
    								entry -> entry.getValue())
    						);
    				coordinates2count.put(coord, 0.0);
    				//System.out.println("\t"+coord);
    			}
    			value2coordinates2count.put(value, coordinates2count);
    			
                exec.checkCanceled();

    		}
    		
    		node2value2coordinates2count.put(n, value2coordinates2count);
    		

    	}
    	

    	Map<NodeCategorical,DataTableToBNMapper> node2mapper = 
    			DataTableToBNMapper.createMapper(
	        		learnt, 
	        		ilogger);
	        
    	// associate each node with its index
    	Map<NodeCategorical,Integer> node2column = 
    			nodesToLearn.stream()
					.collect(
							Collectors.toMap(
									Function.identity(), 
									n -> new Integer(sample.getDataTableSpec().findColumnIndex(n.getName()))
							));

    	
    	// iterate each row of data, and learn the count to later fill in the BN
    	Iterator<DataRow> itRows = sample.iterator();
    	
    	Set<NodeCategorical> failedNodes = new HashSet<NodeCategorical>();
    	
    	// TODO manage long!!!
    	int i = 0;
    	while (itRows.hasNext()) {
    		DataRow row = itRows.next();
    		
    		// for each node to learn...
    		for (NodeCategorical nodeToLearn: nodesToLearn) {
    			    			
    			// identify the value for this node
    			
    			final DataCell cellValue = row.getCell(node2column.get(nodeToLearn));
    			final String value = node2mapper.get(nodeToLearn).getStringValueForCell(cellValue);
    			
    			
    			// TODO deal with missing values!!!
    			Map<NodeCategorical,String> coordinate = 
    					nodeToLearn.getParents()
								.stream()
								.collect( 
										Collectors.toMap( 
												Function.identity(), 
												n -> node2mapper.get(n).getStringValueForCell(row.getCell(node2column.get(n)))
												)
										);

    			try {
	    			//System.out.println(nodeToLearn+"="+value+" | "+coordinate);
	
    				Double previousCount = node2value2coordinates2count.get(nodeToLearn).get(value).get(coordinate);
	    			
	    			double toAdd = 1;
	    			if (useWeightColumn)
	    				toAdd = ((DoubleValue)row.getCell(idxWeightColumn)).getDoubleValue();
	    			
	    			node2value2coordinates2count.get(nodeToLearn).get(value).put(coordinate, previousCount+toAdd);
	    			
	    			//System.out.println(nodeToLearn.getName()+"="+value+" | "+coordinate+" => "+(previousCount+1));
    			} catch (NullPointerException e) {
    				// the content of columns does not fit the domains of the nodes !!!
    				logger.error(
    						"unknown value "+nodeToLearn.getName()+"="+value+
    						", domain is "+nodeToLearn.getDomain()
    						);
    				//logger.error("unable to process "+nodeToLearn.getName()+"="+value+" | "+coordinate);
    				failedNodes.add(nodeToLearn);
    			}
    		}
    		
    		if (i % 10 == 0) { // TODO granularity?
	            // check if the execution monitor was canceled
	            exec.checkCanceled();
	            exec.setProgress(
	            		0.7 * (double)i / sample.size(), 
	            		"reading sample " + i);
        	}
    		i++;
    	}
    	
    	if (!failedNodes.isEmpty()) {
    		logger.error("the following nodes were not measured: "+failedNodes);
    		nodesToLearn.removeAll(failedNodes);
    	}
    	
    	exec.setProgress("aggregating statistics");
    	
    	Set<String> warnings = new HashSet<>();
    	
    	int n = 0;
    	for (NodeCategorical node: nodesToLearn) {

            exec.checkCanceled();
            exec.setProgress(
            		0.7 + 0.3 * (double)n++ / nodesToLearn.size(), 
            		"aggregating statistics for " + node.name);
            
    		// count all the measures for this 
    		/*int total = node2value2coordinates2count.get(node).values()
    							.stream()
    							.mapToInt( coordinates2count -> coordinates2count.values()
    															.stream()
    															.mapToInt(Integer::intValue)
    															.sum())
    							.sum();
    		
    		System.out.println(node.getName()+": total "+total);
    		*/
            
    		for (String value: node.getDomain()) {
    			
    			//String value = valueRaw.toLowerCase();
    			
        		// count all the measures for this 
    			
    			
    			/*
        		int totalValue = node2value2coordinates2count.get(node)
        				node2value2coordinates2count.get(node).get(value).values()
        							.stream()
        							.mapToInt(Integer::intValue)
        							.sum();
        		*/
        		//System.out.println(node.getName()+"="+value+": total "+totalValue);
        		
        		
    			//System.out.println("parents: "+node.getParents());
    			IteratorCategoricalVariables itDomains = learnt.iterateDomains(node.getParents());
    			while (itDomains.hasNext()) {
    				
    				Map<NodeCategorical,String> coord = itDomains.next();
    				
    				//System.out.println("coord: "+coord);
    				
    				Map<NodeCategorical,String> coordLower = coord.entrySet().stream().collect(
    						Collectors.toMap(
    								entry -> entry.getKey(), 
    								entry -> entry.getValue() 
    								)
    						);
    				
    				double totalValue = node.getDomain().stream()
		    				.mapToDouble( val -> node2value2coordinates2count.get(node).get(val).get(coord) + constantToAdd)
		    				.sum();
	
    				double p;
    				
    				if (totalValue == constantToAdd * node.getDomainSize()) {
    					//p = 0;
    					
    					if (methodNoCaseEquiproba) {
    						warnings.add("there is no data for the case "+coord+"; will assume equiprobability");
    					
	    					// no case found. Hard to say :-/ 
	    					p = 1.0/node.getDomainSize();
    					} else {
    						warnings.add("there is no data for the case "+coord+"; will keep former probabilities");
        					p = -1;
    					}
    				} else {
	
	    				
	    				double countForCoord = node2value2coordinates2count.get(node).get(value).get(coord) + constantToAdd;
	    				// / node.getDomainSize()
	    				p = (double)countForCoord / totalValue; // * node.getParentsDimensionality(); // totalValue
	    				//System.out.println(node.getName()+"="+value+" | "+coord+" => "+countForCoord+"/"+totalValue+" => "+p);

    				}
    				
    				if (p >= 0) {
	    				try {
	    					//double previous = node.getProbability(value, coordLower);
	    					node.setProbabilities(p, 
												value, 
												coordLower);
	    					//System.out.println("setting "+node.getName()+"="+value+" | "+coord+" = "+p+" (instead of "+previous+")");
	    				} catch (IllegalArgumentException e) {
	    					e.printStackTrace();
	    					throw e;
	    				}
    				}
	    			
    			}
    			
    		}
    		
    		node.normalize();
    		
        	//System.err.println(node.collectInvalidityReasons());

    		System.out.println(node.asFactor().toStringLong());
    	}
    	
    	exec.checkCanceled();
    	exec.setMessage("processing warnings");
    	
    	for (String warn: warnings) {
    		logger.warn(warn);
    	}
    	
    	System.out.println(learnt.collectInvalidProblems());
    	
    	// TODO dictionnary to map values to something else?
    	
        // once we are done, we return the novel BN
		return new BayesianNetworkPortObject[] { 
				new BayesianNetworkPortObject(learnt) 
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
    	return new BayesianNetworkPortSpec[] { new BayesianNetworkPortSpec() };
	}

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {

    	m_constant.saveSettingsTo(settings);
    	m_useWeightColumn.saveSettingsTo(settings);
    	m_colnameWeight.saveSettingsTo(settings);
    	m_methodNoCase.saveSettingsTo(settings);
    	
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
           
    	m_constant.loadSettingsFrom(settings);
    	m_useWeightColumn.loadSettingsFrom(settings);
    	m_colnameWeight.loadSettingsFrom(settings);
    	m_methodNoCase.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
           
    	m_constant.validateSettings(settings);
    	m_useWeightColumn.validateSettings(settings);
    	m_colnameWeight.validateSettings(settings);
    	m_methodNoCase.validateSettings(settings);
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

