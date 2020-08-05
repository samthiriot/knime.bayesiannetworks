package ch.resear.thiriot.knime.bayesiannetworks.writer;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "BNXMLNBIFWriterNode" Node.
 * Writes the Bayesian network into an XML BIF file.
 *
 * @author Samuel Thiriot
 */
public class BNXMLNBIFWriterNodeFactory 
        extends NodeFactory<BNXMLNBIFWriterNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public BNXMLNBIFWriterNodeModel createNodeModel() {
        return new BNXMLNBIFWriterNodeModel();
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
    public NodeView<BNXMLNBIFWriterNodeModel> createNodeView(final int viewIndex,
            final BNXMLNBIFWriterNodeModel nodeModel) {
        return null;
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
        return new BNXMLNBIFWriterNodeDialog();
    }

}

