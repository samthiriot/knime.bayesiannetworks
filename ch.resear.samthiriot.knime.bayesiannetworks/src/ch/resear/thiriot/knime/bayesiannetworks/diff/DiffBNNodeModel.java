package ch.resear.thiriot.knime.bayesiannetworks.diff;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.MissingCell;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.DoubleCell.DoubleCellFactory;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.IntCell.IntCellFactory;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.def.StringCell.StringCellFactory;
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

import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortObject;


/**
 * TODO overrite checkbox
 * 
 * This is the model implementation of BNXMLNBIFWriterNode.
 * Writes the Bayesian network into an XML BIF file.
 *
 * @author Samuel Thiriot
 */
public class DiffBNNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(DiffBNNodeModel.class);



    /**
     * Constructor for the node model.
     */
    protected DiffBNNodeModel() {

        super(
	    		new PortType[]{ BayesianNetworkPortObject.TYPE, BayesianNetworkPortObject.TYPE },
	    		new PortType[] { BufferedDataTable.TYPE }
	    		);
        
    }
    
    private DataTableSpec createSpecsForOutputTable() {

    	List<DataColumnSpec> specs = new LinkedList<DataColumnSpec>(
			Arrays.asList(
				new DataColumnSpecCreator("variable name", StringCell.TYPE).createSpec(),
				new DataColumnSpecCreator("domain size", IntCell.TYPE).createSpec(),
				new DataColumnSpecCreator("count parents", IntCell.TYPE).createSpec(),
				new DataColumnSpecCreator("cardinality", IntCell.TYPE).createSpec(),
	    		new DataColumnSpecCreator("MSE", DoubleCell.TYPE).createSpec(),
	    		new DataColumnSpecCreator("count zeros net1", IntCell.TYPE).createSpec(),
	    		new DataColumnSpecCreator("count zeros net2", IntCell.TYPE).createSpec(),
	    		new DataColumnSpecCreator("abs diff zeros", IntCell.TYPE).createSpec()
	    	)
		);
	    
    	return new DataTableSpec(specs.toArray(new DataColumnSpec[specs.size()]));
	}




	@Override
	protected PortObject[] execute(
			final PortObject[] inObjects, 
			ExecutionContext exec) throws Exception {

		// retrieve the BN from the input
    	CategoricalBayesianNetwork bn1 = null;
    	try {
    		BayesianNetworkPortObject capsule = (BayesianNetworkPortObject)inObjects[0];
    		bn1 = capsule.getBN();
    	} catch (ClassCastException e) {
    		throw new IllegalArgumentException("The input should be a Bayesian network", e);
    	}
    	CategoricalBayesianNetwork bn2 = null;
    	try {
    		BayesianNetworkPortObject capsule = (BayesianNetworkPortObject)inObjects[1];
    		bn2 = capsule.getBN();
    	} catch (ClassCastException e) {
    		throw new IllegalArgumentException("The input should be a Bayesian network", e);
    	}
    	
    	exec.setProgress(0.0, "comparing the structure");
    	
    	// ensure there are the same variables
    	if (bn1.getNodes().size() != bn2.getNodes().size())
    		throw new InvalidSettingsException("the two input Bayesian networks should have the same structure; yet they do not have the same number of variables"); 
    	for (NodeCategorical n1: bn1.enumerateNodes()) {
    		// check same names
    		NodeCategorical n2 = bn2.getVariable(n1.getName());
    		if (n2 == null)
    			throw new InvalidSettingsException("the second Bayesian network does not contain any node named "+n1.getName());
    		// check same domains
    		if (!n1.getDomain().equals(n2.getDomain()))
    			throw new InvalidSettingsException("variable "+n1.getName()+" has different domains in the two networks");
    		// check parent names
    		if (n1.getParents().size() != n2.getParents().size())
    			throw new InvalidSettingsException("the two Bayesian networks do not have the same number of parents for variable "+n1.getName());
    		Set<String> n1ParentNames = n1.getParents().stream().map(n -> n.name).collect(Collectors.toSet());
    		Set<String> n2ParentNames = n2.getParents().stream().map(n -> n.name).collect(Collectors.toSet());
    		if (!n1ParentNames.equals(n2ParentNames))
    			throw new InvalidSettingsException("the node "+n1.getName()+" has different parents in the two networks");
    		// check probas
    		// TODO to use for "are equal?"
    		//if (!Arrays.equals(n1.getContent(), n2.getContent()))
    		//	throw new InvalidSettingsException("the node "+n1.getName()+" has different probabilities in the two networks");
    	}

		exec.setProgress(0.1, "measuring differences");
		BufferedDataContainer container = exec.createDataContainer(createSpecsForOutputTable());
		
		int rowId = 0;
		double cumulatedMSE = 0.0;
		int cumulatedDiffZeros = 0;
		for (NodeCategorical n1: bn1.enumerateNodes()) {
			final String name = n1.getName();
			
			exec.setProgress(
							0.1+0.9*((double)rowId)/bn1.nodes.size(), 
							"processing variable "+name);
			
			
			NodeCategorical n2 = bn2.getVariable(name);
			
			double[] p1 = n1.getContent();
			double[] p2 = n2.getContent();
			int countZeros1 = 0;
			int countZeros2 = 0;
			
			double mse = 0.0;
			for (int i=0; i<p1.length; i++) {
				mse += Math.pow(p1[i] - p2[i], 2);
				if (p1[i] == 0)
					countZeros1++;
				if (p2[i] == 0)
					countZeros2++;
			}
			mse = mse / p1.length;
			int diffZeros = Math.abs(countZeros1 - countZeros2);
			cumulatedMSE += mse;
			cumulatedDiffZeros += diffZeros; 
			
			container.addRowToTable(
				new DefaultRow(
						new RowKey("Row "+(rowId++)), 
						StringCellFactory.create(name),
						IntCellFactory.create(n1.getDomainSize()),
						IntCellFactory.create(n1.getParents().size()),
						IntCellFactory.create(n1.getCardinality()),
						DoubleCellFactory.create(mse),
						IntCellFactory.create(countZeros1),
						IntCellFactory.create(countZeros2),
						IntCellFactory.create(diffZeros)
						)
				);
			
		}
		
		MissingCell missing = new MissingCell("not relevant");
		
		container.addRowToTable(
				new DefaultRow(
					new RowKey("Row "+(rowId++)), 
					StringCellFactory.create("total"),
					missing,
					missing,
					missing,
					DoubleCellFactory.create(cumulatedMSE),
					missing,
					missing,
					IntCellFactory.create(cumulatedDiffZeros)
					)
		);
		
		container.close();
		
		
		return new BufferedDataTable[] { container.getTable() };
		

	}


    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
     
    }
    
    @Override
	protected DataTableSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
	
        return new DataTableSpec[] { createSpecsForOutputTable() };
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

