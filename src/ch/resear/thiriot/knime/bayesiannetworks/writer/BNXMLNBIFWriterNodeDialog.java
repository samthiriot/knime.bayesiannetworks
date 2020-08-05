package ch.resear.thiriot.knime.bayesiannetworks.writer;

import javax.swing.JFileChooser;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentFileChooser;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * <code>NodeDialog</code> for the "BNXMLNBIFWriterNode" Node.
 * Writes the Bayesian network into an XML BIF file.
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author Samuel Thiriot
 */
public class BNXMLNBIFWriterNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring BNXMLNBIFWriterNode node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected BNXMLNBIFWriterNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentFileChooser(
        		new SettingsModelString("filename", null),
        		"bayesian_network",
        		JFileChooser.SAVE_DIALOG,
        		false
        		));  
    }
}

