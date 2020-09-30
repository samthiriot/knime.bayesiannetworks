package ch.resear.thiriot.knime.bayesiannetworks.create.addnode;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentSeed;
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
public class AddNodeToBNNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring SampleFromBNNode node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected AddNodeToBNNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentSeed(
        	    new SettingsModelSeed(
        				"seed", 
        				System.currentTimeMillis(), 
        				false),
        	    "seed"
        	    ));
    }
}

