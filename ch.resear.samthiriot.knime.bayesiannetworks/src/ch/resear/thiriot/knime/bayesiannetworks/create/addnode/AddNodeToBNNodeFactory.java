package ch.resear.thiriot.knime.bayesiannetworks.create.addnode;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "AugmentSampleWithBNNode" Node.
 * Adds additional columns based on the probabilities from a Bayesian network
 *
 * @author Samuel Thiriot
 */
public class AddNodeToBNNodeFactory 
        extends NodeFactory<AddNodeToBNNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public AddNodeToBNNodeModel createNodeModel() {
        return new AddNodeToBNNodeModel();
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
    public NodeView<AddNodeToBNNodeModel> createNodeView(final int viewIndex,
            final AddNodeToBNNodeModel nodeModel) {
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
        return new AddNodeToBNNodeDialog();

    }

}

