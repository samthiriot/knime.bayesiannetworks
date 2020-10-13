package ch.resear.thiriot.knime.bayesiannetworks.reader;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentFileChooser;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

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
public class XMLBIFBNReaderNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring XMLBIFBNReader node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected XMLBIFBNReaderNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentFileChooser(
        		new SettingsModelString("filename", null),
        		"bayesian_network",
        		".xmlbif|.xbif" // TODO other extensions? 
        		));
          
    }
}

