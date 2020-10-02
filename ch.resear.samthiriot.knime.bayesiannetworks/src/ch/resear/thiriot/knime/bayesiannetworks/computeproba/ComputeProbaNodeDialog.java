package ch.resear.thiriot.knime.bayesiannetworks.computeproba;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * 
 * @author Samuel Thiriot
 */
public class ComputeProbaNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring SampleFromBNNode node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected ComputeProbaNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentString(
        		new SettingsModelString("colname", "probability"),
        		"column name",
        		true, 
        		30
        		));
                  
    }
}

