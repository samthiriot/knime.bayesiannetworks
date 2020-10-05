package ch.resear.thiriot.knime.bayesiannetworks.learn;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;

/**
 * @author Samuel Thiriot
 */
public class LearnBNFromSampleNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring SampleFromBNNode node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected LearnBNFromSampleNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentNumber(
        		new SettingsModelIntegerBounded("m_addconstant", 0, 0, 1000),
                "Smoothing constant:", 1, 5));
             
    }
}

