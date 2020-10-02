package ch.resear.thiriot.knime.bayesiannetworks.sample;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.DialogComponentSeed;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelSeed;

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
public class SampleFromBNNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring SampleFromBNNode node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected SampleFromBNNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentNumber(
                new SettingsModelIntegerBounded(
                    SampleFromBNNodeModel.CFGKEY_COUNT,
                    SampleFromBNNodeModel.DEFAULT_COUNT,
                    0, Integer.MAX_VALUE),
                    "Samples:", /*step*/ 1, /*componentwidth*/ 5));
                  
        addDialogComponent(new DialogComponentSeed(
        	    new SettingsModelSeed(
        				"seed", 
        				System.currentTimeMillis(), 
        				false),
        	    "seed"
        	    ));
        
        SettingsModelBoolean m_threadsAuto = new SettingsModelBoolean(
        		"m_threads_auto", 
        		true);
        SettingsModelIntegerBounded m_threads = new SettingsModelIntegerBounded(
        		"m_threads", 
        		Runtime.getRuntime().availableProcessors(), 1, 128);
        m_threads.setEnabled(!m_threadsAuto.getBooleanValue());
        m_threadsAuto.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
		        m_threads.setEnabled(!m_threadsAuto.getBooleanValue());
			}
		});
        addDialogComponent(new DialogComponentBoolean(m_threadsAuto, "use all CPUs"));
        addDialogComponent(new DialogComponentNumber(m_threads, "max CPUs to use", 1));
        
    }
}

