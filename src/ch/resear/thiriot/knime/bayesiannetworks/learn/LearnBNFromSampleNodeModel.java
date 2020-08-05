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
    	
    	
    	// no parameter to retrieve
    	
    	
    	
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
    
    	
    	// prepare the empty data structure
    	Map<NodeCategorical,
    		Map<String,
    			Map<Map<NodeCategorical,
    					String>,
    				Integer>>> node2value2coordinates2count = new HashMap<>();
    	
    	for (NodeCategorical n: nodesToLearn) {
    		
    		Map<String,Map<Map<NodeCategorical,String>,Integer>> value2coordinates2count = new HashMap<>();
    		
    		for (String value : n.getDomain()) {
    			//String value = valueRaw.toLowerCase();
    			System.out.println("studying "+n.getName()+"="+value);
    			Map<Map<NodeCategorical,String>,Integer> coordinates2count = new HashMap<>();
    			IteratorCategoricalVariables itDomains = learnt.iterateDomains(n.getParents());
    			while (itDomains.hasNext()) {
    				Map<NodeCategorical,String> coord = itDomains.next().entrySet().stream().collect(
    						Collectors.toMap(
    								entry -> entry.getKey(), 
    								entry -> entry.getValue())
    						);
    				coordinates2count.put(coord, 0);
    				System.out.println("\t"+coord);
    			}
    			value2coordinates2count.put(value, coordinates2count);
    			
                exec.checkCanceled();

    		}
    		
    		node2value2coordinates2count.put(n, value2coordinates2count);
    		

    	}
    	

    	Map<NodeCategorical,DataTableToBNMapper> node2mapper = 
    			DataTableToBNMapper.createMapper(
	        		learnt, 
	        		logger);
	        
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
	
	    			Integer previousCount = node2value2coordinates2count.get(nodeToLearn).get(value).get(coordinate);
	    			node2value2coordinates2count.get(nodeToLearn).get(value).put(coordinate, previousCount+1);
	    			
	    			System.out.println(nodeToLearn.getName()+"="+value+" | "+coordinate+" => "+(previousCount+1));
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
    		
    		if (i % 100 == 0) { // TODO granularity?
	            // check if the execution monitor was canceled
	            exec.checkCanceled();
	            exec.setProgress(
	            		(double)i / sample.size(), 
	            		"reading sample " + i);
        	}
    		i++;
    	}
    	
    	if (!failedNodes.isEmpty()) {
    		logger.error("the following nodes were not measured: "+failedNodes);
    		nodesToLearn.removeAll(failedNodes);
    	}
    	
    	Set<String> warnings = new HashSet<>();
    	
    	for (NodeCategorical node: nodesToLearn) {
    		
    		// count all the measures for this 
    		int total = node2value2coordinates2count.get(node).values()
    							.stream()
    							.mapToInt( coordinates2count -> coordinates2count.values()
    															.stream()
    															.mapToInt(Integer::intValue)
    															.sum())
    							.sum();
    		
    		System.out.println(node.getName()+": total "+total);
    		
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
        		
        		
    			System.out.println("parents: "+node.getParents());
    			IteratorCategoricalVariables itDomains = learnt.iterateDomains(node.getParents());
    			while (itDomains.hasNext()) {
    				
    				Map<NodeCategorical,String> coord = itDomains.next();
    				
    				System.out.println("coord: "+coord);
    				
    				Map<NodeCategorical,String> coordLower = coord.entrySet().stream().collect(
    						Collectors.toMap(
    								entry -> entry.getKey(), 
    								entry -> entry.getValue() 
    								)
    						);
    				
    				int totalValue = node.getDomain().stream()
		    				.mapToInt( val -> node2value2coordinates2count.get(node).get(val).get(coord) )
		    				.sum();
	
    				double p;
    				
    				if (totalValue == 0) {
    					p = 0;
    					
    					warnings.add("there is no data for the case "+coord+"; will assume equiprobability");
    					
    					// no case found. Hard to say :-/ 
    					p = 1.0/node.getDomainSize();
    					
    				} else {
	
	    				
	    				int countForCoord = node2value2coordinates2count.get(node).get(value).get(coord);
	    				// / node.getDomainSize()
	    				p = (double)countForCoord / totalValue; // * node.getParentsDimensionality(); // totalValue
	    				System.out.println(node.getName()+"="+value+" | "+coord+" => "+countForCoord+"/"+totalValue+" => "+p);

    				}
    				

    				try {
    					node.setProbabilities(p, 
											value, 
											coordLower);
    				} catch (IllegalArgumentException e) {
    					e.printStackTrace();
    					throw e;
    				}
	    			
    			}
    			
    		}
    		
    		// TODO not implemented o_O
    		//node.normalize();
    		
        	System.err.println(node.collectInvalidityReasons());

    		System.out.println(node.asFactor().toStringLong());
    	}
    	
    	
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
        // TODO Code executed on reset.
        // Models build during execute are cleared here.
        // Also data handled in load/saveInternals will be erased here.
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

