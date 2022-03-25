package ch.resear.thiriot.knime.bayesiannetworks.port;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.commons.lang3.SerializationUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.ModelContentRO;
import org.knime.core.node.ModelContentWO;
import org.knime.core.node.port.AbstractSimplePortObjectSpec;

import ch.resear.thiriot.knime.bayesiannetworks.lib.bn.NodeCategorical;

public class BayesianNetworkPortSpec extends AbstractSimplePortObjectSpec {

	/**
     * @noreference This class is not intended to be referenced by clients.
     * @since 3.0
     */
    public static final class Serializer extends AbstractSimplePortObjectSpecSerializer<BayesianNetworkPortSpec> {}

    private Map<String,List<String>> variableName2modalities;
    private List<String> orderedVariableNames = null;
    
	public BayesianNetworkPortSpec() {
		this.variableName2modalities = Collections.emptyMap();
	}

	public BayesianNetworkPortSpec(
			Map<String,List<String>> variableName2modalities) {
		this.variableName2modalities = SerializationUtils.clone(new HashMap<>(variableName2modalities));
	}
	
	public Collection<String> getVariableNames() {
		return variableName2modalities.keySet();
	}
	
	/**
	 * Return the list of the variable sorted in natural order
	 * @return
	 */
	public List<String> getSortedVariableNames() { 
		
		if (orderedVariableNames != null)
			return orderedVariableNames;
		
		orderedVariableNames = new LinkedList<>(variableName2modalities.keySet());
		Collections.sort(orderedVariableNames);
		
		orderedVariableNames = Collections.unmodifiableList(orderedVariableNames);
		return orderedVariableNames;
	}
	
	public List<String> getModalities(String variable) {
		return Collections.unmodifiableList(this.variableName2modalities.get(variable));
	}
	
	public Map<String,List<String>> getVariableAndModalities() {
		return Collections.unmodifiableMap(this.variableName2modalities);
	}
	
	@Override
	public JComponent[] getViews() {
		
		JScrollPane spNodes;
		{
			StringBuffer sb = new StringBuffer();
			sb.append(this.variableName2modalities.size()).append(" node");
			if (this.variableName2modalities.size() > 1)
				sb.append("s");
			
			if (!this.variableName2modalities.isEmpty()) {
				sb.append(":\n");
				this.variableName2modalities.keySet().stream()
					.map(n -> "- " +n+" "+NodeCategorical.getStrRepresentationOfDomain(this.variableName2modalities.get(n))+"\n")
					.forEach(s -> sb.append(s));
			}
			
			JTextArea c = new JTextArea(sb.toString());
			c.setLineWrap(true);
			
			spNodes = new JScrollPane(c); 
			spNodes.setName("Nodes");
		}
		
		return new JComponent [] { spNodes };
	}

	@Override
	protected void save(ModelContentWO model) {
		model.addByteArray("variables2domain", SerializationUtils.serialize(new HashMap<String,List<String>>(this.variableName2modalities)));
	}

	@Override
	protected void load(ModelContentRO model) throws InvalidSettingsException {
		variableName2modalities = SerializationUtils.deserialize(model.getByteArray("variables2domain"));
	}

}
