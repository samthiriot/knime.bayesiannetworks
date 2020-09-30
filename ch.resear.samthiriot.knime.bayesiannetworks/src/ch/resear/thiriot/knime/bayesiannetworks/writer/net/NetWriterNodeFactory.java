package ch.resear.thiriot.knime.bayesiannetworks.writer.net;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "BNXMLNBIFWriterNode" Node.
 * Writes the Bayesian network into an XML BIF file.
 *
 * @author Samuel Thiriot
 */
public class NetWriterNodeFactory 
        extends NodeFactory<NetWriterNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public NetWriterNodeModel createNodeModel() {
        return new NetWriterNodeModel();
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
    public NodeView<NetWriterNodeModel> createNodeView(final int viewIndex,
            final NetWriterNodeModel nodeModel) {
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
        return new NetWriterNodeDialog();
    }

}

