package ch.resear.thiriot.knime.bayesiannetworks.learn;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.data.DoubleValue;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.DialogComponentStringSelection;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelColumnName;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * @author Samuel Thiriot
 */
public class LearnBNFromSampleNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring SampleFromBNNode node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    @SuppressWarnings("unchecked")
	protected LearnBNFromSampleNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentNumber(
        		new SettingsModelIntegerBounded("m_addconstant", 0, 0, 1000),
                "Smoothing constant:", 1, 5));
             
        SettingsModelBoolean m_useWeightColumn = new SettingsModelBoolean(
        		"m_use_weight_colum", false);
        
        SettingsModelColumnName m_colnameWeight = new SettingsModelColumnName("m_colname", null);
        m_colnameWeight.setEnabled(m_useWeightColumn.getBooleanValue());
        m_useWeightColumn.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
		        m_colnameWeight.setEnabled(m_useWeightColumn.getBooleanValue());
			}
		});
        
        addDialogComponent(new DialogComponentBoolean(
        		m_useWeightColumn,
                "Data contains a weight column"));
        addDialogComponent(new DialogComponentColumnNameSelection(
        		m_colnameWeight,
        		"Weight column",
        		1,
        		false,
        		DoubleValue.class
        		));
        
        addDialogComponent(new DialogComponentStringSelection(
        		new SettingsModelString(
        	    		"m_method_no_vase", 
        	    		LearnBNFromSampleNodeModel.METHOD_NOCASE_PREVIOUS), 
        		"deal with no case", 
        		LearnBNFromSampleNodeModel.METHOD_NOCASE_PREVIOUS,
        		LearnBNFromSampleNodeModel.METHOD_NOCASE_EQUIPROBABILITY
        		));
        
    }
}

