package ch.resear.thiriot.knime.bayesiannetworks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.math.NumberUtils;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataType;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.BooleanCell.BooleanCellFactory;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.DoubleCell.DoubleCellFactory;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.IntCell.IntCellFactory;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.def.StringCell.StringCellFactory;
import org.knime.core.node.NodeLogger;

import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;

// TODO manage intervals

public class DataTableToBNMapper {

	public final NodeCategorical node;
	private DataColumnSpec spec = null;
	
	protected final ILogger logger;
	
	private DataType knimeType;
	
	private Map<DataCell,String> cell2string = new HashMap<>();
	private Map<String,DataCell> string2cell = new HashMap<>();
	
	
    public static Map<NodeCategorical,DataTableToBNMapper> createMapper(
    		CategoricalBayesianNetwork bn, 
    		ILogger logger) {
    	return bn.getNodes().stream().collect(
    			Collectors.toMap( 
    					n -> n, 
    					n -> new DataTableToBNMapper(n, logger)
    					)
    			);
    }
    
    
    
    
	public DataTableToBNMapper(NodeCategorical node, 
			ILogger logger) {
		
		this.node = node;
		this.logger = logger;
		
		getSpecForNode();
	}

    public DataColumnSpec getSpecForNode() {
    	
    	if (spec != null)
    		return spec;
    	
    	// TODO detect ranges as intervals
    	
    	
    	
    	final Set<String> domainLower = node.getDomain().stream().map(s -> s.toLowerCase()).collect(Collectors.toSet());
    	
    	if ( node.getDomainSize()==2 
    			&& domainLower.contains("true")
    			&& domainLower.contains("false")) {
    	
    		knimeType = BooleanCell.TYPE;
    		logger.info("the domain of variable "+node.getName()+" will be considered as Boolean");
    		
    	} else if (node.getDomain().stream().allMatch(s -> NumberUtils.isCreatable(s))) {
    		
    		// these are numbers; but are they also integers? 
    		if (node.getDomain().stream().map(s -> Double.parseDouble(s)).allMatch(d -> (double)d.intValue() == d)) {
    			// all integer !
    			logger.info("the domain of variable "+node.getName()+" will be considered as integer values");
        		knimeType = IntCell.TYPE;
        		
    		} else {
    			logger.info("the domain of variable "+node.getName()+" will be considered as double values");
        		knimeType = DoubleCell.TYPE;
        		
    		}
    		
    	} else {
    		knimeType = StringCell.TYPE;
    	}
    	
    	spec = new DataColumnSpecCreator(
    			node.getName(), 
    			knimeType
    			).createSpec();
    	
    	return spec;
    }
    
    
    public DataCell createCellForStringValue(String valueStr) {
    	
    	DataCell res = null;
    	if (knimeType == BooleanCell.TYPE) {
			res = BooleanCellFactory.create(valueStr);
		} else if (knimeType == DoubleCell.TYPE) {
			res = DoubleCellFactory.create(valueStr);
		} else if (knimeType == IntCell.TYPE) {
			res = IntCellFactory.create(valueStr);
		} else {
			res = StringCellFactory.create(valueStr);
		}
    	
    	string2cell.put(valueStr, res);
    	cell2string.put(res, valueStr);
    	
    	return res;
    }
    
    public String getStringValueForCell(DataCell cell) {
    	String res = cell2string.get(cell);
    	
    	if ( (res== null) && (knimeType == BooleanCell.TYPE)) {
			
			cell2string.put(
					BooleanCell.FALSE,
					node.getDomain().stream().filter( s -> s.toLowerCase().equals("false")).findFirst().get()
					);
			
			cell2string.put(
					BooleanCell.TRUE,
					node.getDomain().stream().filter( s -> s.toLowerCase().equals("true")).findFirst().get()
					);
			
			res = cell2string.get(cell);
		}
		
    	if (res == null) {
    		
    		res = cell.toString();
    		cell2string.put(cell, res);
    		logger.debug("unknown value for variable "+node+"="+cell+" (we knew "+cell2string.keySet()+") => adding the mapping to "+cell.toString());
    		// try to identify what we talk about. 
    		//
    			
    		//}
    	}
    	if (res == null)
    		throw new IllegalArgumentException(
    				"unknow content "+cell+
    				" for node "+this.node+"; "+
    				"the known domain is "+this.node.getDomain());
    	return res;
    }
    
}
