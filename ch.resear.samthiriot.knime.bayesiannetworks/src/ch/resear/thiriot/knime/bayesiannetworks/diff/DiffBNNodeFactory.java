package ch.resear.thiriot.knime.bayesiannetworks.diff;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "BNXMLNBIFWriterNode" Node.
 * Writes the Bayesian network into an XML BIF file.
 *
 * @author Samuel Thiriot
 */
public class DiffBNNodeFactory 
        extends NodeFactory<DiffBNNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public DiffBNNodeModel createNodeModel() {
        return new DiffBNNodeModel();
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
    public NodeView<DiffBNNodeModel> createNodeView(final int viewIndex,
            final DiffBNNodeModel nodeModel) {
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
        return new DiffBNNodeDialog();
    }

}

