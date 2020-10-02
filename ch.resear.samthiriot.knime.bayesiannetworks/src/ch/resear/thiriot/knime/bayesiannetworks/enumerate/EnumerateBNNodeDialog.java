package ch.resear.thiriot.knime.bayesiannetworks.enumerate;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentNumberEdit;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelDoubleBounded;

/**
 * <code>NodeDialog</code> for the "SampleFromBNNode" Node.
 * Using a Bayesian network which describes densities of probabilities, this node generates a population of entities (data table) with columns corresponding the variables of the BN.
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author Samuel Thiriot
 */
public class EnumerateBNNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring SampleFromBNNode node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected EnumerateBNNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentBoolean(
        		new SettingsModelBoolean("skip_null", true),
        	    "skip impossible combinations"
        	    ));
        
        SettingsModelBoolean m_skipOnEpsilon = new SettingsModelBoolean("skip_on_epsilon", true); 
        addDialogComponent(new DialogComponentBoolean(
        		m_skipOnEpsilon,
        	    "skip combinations with too low probability"
        	    ));
        
        SettingsModelDoubleBounded m_skipEpsilon = new SettingsModelDoubleBounded("skip_epsilon", 1e-6, 0.0, 1.0);
        m_skipEpsilon.setEnabled(m_skipOnEpsilon.getBooleanValue());
        addDialogComponent(new DialogComponentNumberEdit(
        		m_skipEpsilon, 
        		"lower probability"
        		));
        
        m_skipOnEpsilon.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				m_skipEpsilon.setEnabled(m_skipOnEpsilon.getBooleanValue());
			}
		});
        
    }
}

