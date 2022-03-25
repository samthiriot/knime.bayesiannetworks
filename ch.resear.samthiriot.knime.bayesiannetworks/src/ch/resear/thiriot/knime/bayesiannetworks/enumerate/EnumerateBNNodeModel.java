package ch.resear.thiriot.knime.bayesiannetworks.enumerate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.rank.Median;
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
import org.knime.core.node.defaultnodesettings.SettingsModelDoubleBounded;
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
    private final SettingsModelBoolean m_skipOnEpsilon = new SettingsModelBoolean("skip_on_epsilon", true);
    private final SettingsModelDoubleBounded m_skipEpsilon = new SettingsModelDoubleBounded("skip_epsilon", 1e-6, 0.0, 1.0);

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
    	for (NodeCategorical node: bn.getNodesSortedByName()) {
    		
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
    	
    	BayesianNetworkPortSpec specBN = (BayesianNetworkPortSpec) inSpecs[0];
    	
    	if (specBN != null) {
    		
        	Map<String,DataTableToBNMapper> node2mapper = DataTableToBNMapper.createMapper(specBN, ilogger);

        	List<DataColumnSpec> specs = new LinkedList<DataColumnSpec>();
        	for (String nodeName: specBN.getSortedVariableNames()) {
        		
        		specs.add(node2mapper.get(nodeName).getSpecForNode());
        	}
        	// add a column for the probability
        	specs.add(
        		new DataColumnSpecCreator("probability", DoubleCell.TYPE).createSpec()
        	);
        	
        	DataColumnSpec[] specsArray = specs.toArray(new DataColumnSpec[specs.size()]);
        	
    		return new DataTableSpec[]{ new DataTableSpec(specsArray) };

    	} else 
    		return new DataTableSpec[]{null};
		
    }
    
    protected double computeProbability(
    		List<NodeCategorical> nodeOrderForBest, 
    		Map<NodeCategorical, String> variable2value) {
    	
    	double p = 1.0;
    	
    	for (NodeCategorical node: nodeOrderForBest) {
    		//System.out.println(node);
    		
    		// create the list key/value for only the parents
    		Map<NodeCategorical,String> parent2value = new HashMap<NodeCategorical, String>(variable2value);
    		parent2value.keySet().retainAll(node.getParents());
    		
    		String value = variable2value.get(node);
    		double pp = node.getProbability(value, parent2value);
    		
    		//System.out.println("p "+node+" => "+pp);
    		
    		if (pp == 0) {
    			// if there is an impossibility, it will propagate anyway!
    			return 0;
    		}
    			
    		p = p * pp;
    	}
    	
    	return p;
    	
    }
    
    /**
     * If it is below epsilon, it will return -1
     * 
     * @param nodeOrderForBest
     * @param variable2value
     * @param epsilon
     * @return
     */
    protected double computeProbabilityPruned(
    		List<NodeCategorical> nodeOrderForBest, 
    		Map<NodeCategorical, String> variable2value,
    		double epsilon) {
    	
    	double p = 1.0;
    	
    	//int rank = 0;
    	for (NodeCategorical node: nodeOrderForBest) {
    		//System.out.println(node);
    		//rank++;
    		// create the list key/value for only the parents
    		Map<NodeCategorical,String> parent2value = new HashMap<NodeCategorical, String>(variable2value);
    		parent2value.keySet().retainAll(node.getParents());
    		
    		String value = variable2value.get(node);
    		double pp = node.getProbability(value, parent2value);
    		
    		//System.out.println("p "+node+" => "+pp);
    		
    		if (pp == 0) {
    			// if there is an impossibility, it will propagate anyway!
    			//System.out.println("stop! 0 at rank "+rank);
    			return 0;
    		}
    			
    		p = p * pp;
    		
    		if (p < epsilon) {
    			//System.out.println("stop! epsilon "+p+" at rank "+rank);
    			return -1;
    		}
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
    	
    	final double skipEpsilon = m_skipEpsilon.getDoubleValue();
    	final boolean skipOnEpsilon = m_skipOnEpsilon.getBooleanValue() && skipEpsilon > 0;
    	final boolean skipNull = m_skipNull.getBooleanValue() || (m_skipOnEpsilon.getBooleanValue() && (skipEpsilon==0));
    	
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
    	
    	// create the order to compute probas; we start first with higher counts of zero
    	// which will be eliminated first!
    	List<NodeCategorical> nodeOrderForBest = new ArrayList<>(bn.enumerateNodes());
		Map<NodeCategorical,Double> node2proportionZeros = nodeOrderForBest.stream().collect(
				Collectors.toMap(
	    				n -> n, 
	    				n -> (double)n.getCountOfZeros()/n.getCardinality() ));
    	if (skipOnEpsilon) {
    		// the user asks for small probabilities to be skipped
    		// so the best is to always start with the tables having zeros, as they will always drop below epsilon
    		// many small values (median value)
    		// so we quickly stop the exploration of combinations having to small probabilities

    		Median median = new Median();
    		Map<NodeCategorical,Double> node2median = nodeOrderForBest.stream().collect(
    				Collectors.toMap(
	    				n -> n, 
	    				n -> median.evaluate(n.getContent()) ));
    		
    		nodeOrderForBest.sort(new Comparator<NodeCategorical>() {
    			
				@Override
				public int compare(NodeCategorical o1, NodeCategorical o2) {
					Double proportionOfZeros1 = node2proportionZeros.get(o1);
					Double proportionOfZeros2 = node2proportionZeros.get(o2);
					// first compare on the proportion of zeros: the higher the better
					int r = - proportionOfZeros1.compareTo(proportionOfZeros2);
					
					// or compare on the median: the lowest the better
					if (r == 0) {
						Double median1 = node2median.get(o1);
						Double median2 = node2median.get(o2);
						r = median1.compareTo(median2);
					}
					// of start with the biggest cardinality (because there are more chances to have small values there?)
					if (r==0)
						r = o2.getCardinality()-o1.getCardinality();
					return r;
				}
	    		
			});
    		System.out.println(
    				"nodes sorted according to median:\n"+
					nodeOrderForBest.stream()
    								.map(n -> n.getName()+": median "+
    											node2median.get(n)+", "+
    											n.getCountOfZeros()+" zeros, "+
    											Integer.toString(n.getCardinality())+
    											" values"
    									)
    								.collect(Collectors.joining("\n"))
    				);
    	} else { // if (skipNull) 
	    	// the user asks for null probabilities to be skipped
    		// or anyway a null probability avoids the combination of the rest of a combination!
    		// so the best is to always start with the table having the highest count of zero
    		// so we will first compute the probabilities of the tables having most zeros so we 
    		// quickly drop combinations leading to 0
    		
    		// TODO can be replace with bn.enumerateVariablesPerZeros()
    		
    		nodeOrderForBest.sort(new Comparator<NodeCategorical>() {
	
				@Override
				public int compare(NodeCategorical o1, NodeCategorical o2) {
					Double proportionOfZeros1 = node2proportionZeros.get(o1);
					Double proportionOfZeros2 = node2proportionZeros.get(o2);
					// first compare on the proportion of zeros: the higher the better
					int r = - proportionOfZeros1.compareTo(proportionOfZeros2);
					// then, if equal, compare on size (why?)
					if (r==0)
						r = o1.getCardinality()-o2.getCardinality();
					return r;
				}
	    		
			});
    		// display it to ensure we understood the thing
    		System.out.println(
    				"nodes sorted according to 0:\n"+
					nodeOrderForBest.stream()
    								.map(n -> n.getName()+": "+n.getCountOfZeros()+" zeros, "+	
    										Integer.toString(n.getCardinality())+" values")
    								.collect(Collectors.joining("\n"))
    				);
    	} 
    	
    	// compute the maximum combination feasible 
    	long total = 1;
        for (NodeCategorical node : bn.enumerateNodes())
        	total += total * node.getDomainSize();
        System.out.println("worse total expected "+total);

    	long i=0;
    	long added=0;
    	while (it.hasNext()) {
    		
    		i++;
        	exec.setProgress(
            		((double)i) / total, 
            		"Exploring combination " + i + ", "+added+" rows created");
    		exec.checkCanceled();

    		Map<NodeCategorical, String> variable2value = it.next();
    		//System.out.println(variable2value);
    		
        	//System.out.println("computing the joint probability");
        	//double p = bn.jointProbabilityFromFactors(variable2value);
        	
        	double p;
        	if (skipOnEpsilon) 
        		p = computeProbabilityPruned(nodeOrderForBest, variable2value, skipEpsilon);
        	else 
        		p = computeProbability(nodeOrderForBest, variable2value);

        	if (skipOnEpsilon && p<skipEpsilon)
        		continue;
        	
        	if (skipNull && p==0)
        		continue;
        	
    		// convert to KNIME cells
        	DataCell[] results = new DataCell[variable2value.size()+1];
        	int j=0;
        	for (NodeCategorical node : bn.getNodesSortedByName()) {
        		
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
        	added++;
        	
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
    	m_skipEpsilon.saveSettingsTo(settings);
    	m_skipOnEpsilon.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    	
    	m_skipNull.loadSettingsFrom(settings); 
    	m_skipEpsilon.loadSettingsFrom(settings);
    	m_skipOnEpsilon.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    	
    	m_skipNull.validateSettings(settings);
    	m_skipEpsilon.validateSettings(settings);
    	m_skipOnEpsilon.validateSettings(settings);
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

