package ch.resear.thiriot.knime.bayesiannetworks.create.empty;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;

/**
 * <code>NodeDialog</code> for the "XMLBIFBNReader" Node.
 * Read a Bayesian network from the XML BIF format
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author Samuel Thiriot
 */
public class CreateEmptyNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring XMLBIFBNReader node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected CreateEmptyNodeDialog() {
        super();
        
    }
}

