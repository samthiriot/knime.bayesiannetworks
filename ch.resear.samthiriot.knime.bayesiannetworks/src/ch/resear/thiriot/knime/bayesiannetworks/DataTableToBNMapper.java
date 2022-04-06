package ch.resear.thiriot.knime.bayesiannetworks;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.math.NumberUtils;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnDomainCreator;
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

import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.CategoricalBayesianNetwork;
import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;
import ch.resear.thiriot.knime.bayesiannetworks.port.BayesianNetworkPortSpec;

// TODO manage intervals

public class DataTableToBNMapper {

	//public final NodeCategorical node;
	public final String nodeName;
	public final List<String> nodeDomain;
	private DataColumnSpec spec = null;
	
	protected final ILogger logger;
	
	private DataType knimeType;
	
	private Map<DataCell,String> cell2string = new ConcurrentHashMap<>();
	private Map<String,DataCell> string2cell = new ConcurrentHashMap<>();
	
	
    public static Map<NodeCategorical,DataTableToBNMapper> createMapper(
    		CategoricalBayesianNetwork bn, 
    		ILogger logger) {
    	return bn.getNodesSortedByName().stream().collect(
    			Collectors.toMap( 
    					n -> n, 
    					n -> new DataTableToBNMapper(n, logger)
    					)
    			);
    }
    
    public static Map<String,DataTableToBNMapper> createMapper(
    		BayesianNetworkPortSpec specBN, 
    		ILogger logger) {
    	return specBN.getSortedVariableNames().stream().collect(
    			Collectors.toMap( 
    					s -> s, 
    					s -> new DataTableToBNMapper(s, specBN.getModalities(s), logger)
    					)
    			);
    }
    
    
    
    
	public DataTableToBNMapper(NodeCategorical node, 
			ILogger logger) {
		
		this.nodeName = node.getName();
		this.nodeDomain = node.getDomain();
		this.logger = logger;
		
		getSpecForNode();
	}
	
	public DataTableToBNMapper(String nodeName, List<String> nodeDomain,
			ILogger logger) {
		
		this.nodeName = nodeName;
		this.nodeDomain = nodeDomain;
		this.logger = logger;
		
		getSpecForNode();
	}

    public DataColumnSpec getSpecForNode() {
    	
    	if (spec != null)
    		return spec;
    	
    	// TODO detect ranges as intervals
    	
    	final Set<String> domainLower = nodeDomain.stream().map(s -> s.toLowerCase()).collect(Collectors.toSet());
    	
    	if ( nodeDomain.size()==2 
    			&& domainLower.contains("true")
    			&& domainLower.contains("false")) {
    	
    		logger.info("the domain of variable "+nodeName+" will be considered as Boolean");
    		knimeType = BooleanCell.TYPE;
    		
    		spec = new DataColumnSpecCreator(
        			nodeName, 
        			knimeType
        			). createSpec();
    		
    	} else if (nodeDomain.stream().allMatch(s -> NumberUtils.isCreatable(s))) {
    		
    		DataCell min = null;
    		DataCell max = null;
    		// these are numbers; but are they also integers? 
    		if (nodeDomain.stream().map(s -> Double.parseDouble(s)).allMatch(d -> (double)d.intValue() == d)) {
    			// all integer !
    			logger.info("the domain of variable "+nodeName+" will be considered as integer values");
        		knimeType = IntCell.TYPE;
        		min = IntCellFactory.create(
        				nodeDomain.stream().mapToInt(s -> Integer.parseInt(s)).min().orElseThrow()
        				);
        		max = IntCellFactory.create(
        				nodeDomain.stream().mapToInt(s -> Integer.parseInt(s)).max().orElseThrow()
        				);
        		
    		} else {
    			logger.info("the domain of variable "+nodeName+" will be considered as double values");
        		knimeType = DoubleCell.TYPE;
        		min = DoubleCellFactory.create(
        				nodeDomain.stream().mapToDouble(s -> Double.parseDouble(s)).min().orElseThrow()
        				);
        		max = DoubleCellFactory.create(
        				nodeDomain.stream().mapToDouble(s -> Double.parseDouble(s)).max().orElseThrow()
        				);
        		
    		}
    	
    		DataColumnSpecCreator creator = new DataColumnSpecCreator(
	    			nodeName, 
	    			knimeType
	    			);
    		creator.setDomain(new DataColumnDomainCreator(
    				min, 
    				max
    				).createDomain());
    		spec = creator.createSpec();
	    	
    	}  else {
    	
			knimeType = StringCell.TYPE;
			Set<DataCell> domainValues = nodeDomain.stream().map(v -> StringCellFactory.create(v)).collect(Collectors.toSet());
			
			DataColumnSpecCreator creator = new DataColumnSpecCreator(
	    			nodeName, 
	    			knimeType
	    			);
			creator.setDomain(new DataColumnDomainCreator(domainValues).createDomain());
			spec = creator.createSpec();
    	}
    	
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
					nodeDomain.stream().filter( s -> s.toLowerCase().equals("false") ).findFirst().get()
					);
			
			cell2string.put(
					BooleanCell.TRUE,
					nodeDomain.stream().filter( s -> s.toLowerCase().equals("true") ).findFirst().get()
					);
			
			res = cell2string.get(cell);
		}
		
    	if (res == null) {
    		
    		res = cell.toString();
    		cell2string.put(cell, res);
    		logger.debug("unknown value for variable "+nodeName+"="+cell+" (we knew "+cell2string.keySet()+") => adding the mapping to "+cell.toString());
    		// try to identify what we talk about. 
    		//
    			
    		//}
    	}
    	if (res == null)
    		throw new IllegalArgumentException(
    				"unknow content "+cell+
    				" for node "+this.nodeName+"; "+
    				"the known domain is "+this.nodeDomain);
    	return res;
    }
    
}
