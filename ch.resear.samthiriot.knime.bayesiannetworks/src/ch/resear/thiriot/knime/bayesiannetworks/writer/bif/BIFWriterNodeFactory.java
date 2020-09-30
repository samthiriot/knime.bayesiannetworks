package ch.resear.thiriot.knime.bayesiannetworks.writer.bif;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "BNXMLNBIFWriterNode" Node.
 * Writes the Bayesian network into an XML BIF file.
 *
 * @author Samuel Thiriot
 */
public class BIFWriterNodeFactory 
        extends NodeFactory<BIFWriterNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public BIFWriterNodeModel createNodeModel() {
        return new BIFWriterNodeModel();
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
    public NodeView<BIFWriterNodeModel> createNodeView(final int viewIndex,
            final BIFWriterNodeModel nodeModel) {
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
        return new BIFWriterNodeDialog();
    }

}

