package ch.resear.thiriot.knime.bayesiannetworks.reader;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "XMLBIFBNReader" Node.
 * Read a Bayesian network from the XML BIF format
 *
 * @author Samuel Thiriot
 */
public class XMLBIFBNReaderNodeFactory 
        extends NodeFactory<XMLBIFBNReaderNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLBIFBNReaderNodeModel createNodeModel() {
        return new XMLBIFBNReaderNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<XMLBIFBNReaderNodeModel> createNodeView(final int viewIndex,
            final XMLBIFBNReaderNodeModel nodeModel) {
       
    	return null;
    	//return new XMLBIFBNReaderNodeView(nodeModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new XMLBIFBNReaderNodeDialog();
    }

}

