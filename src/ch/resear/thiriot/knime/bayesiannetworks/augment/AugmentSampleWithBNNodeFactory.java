package ch.resear.thiriot.knime.bayesiannetworks.augment;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "AugmentSampleWithBNNode" Node.
 * Adds additional columns based on the probabilities from a Bayesian network
 *
 * @author Samuel Thiriot
 */
public class AugmentSampleWithBNNodeFactory 
        extends NodeFactory<AugmentSampleWithBNNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public AugmentSampleWithBNNodeModel createNodeModel() {
        return new AugmentSampleWithBNNodeModel();
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
    public NodeView<AugmentSampleWithBNNodeModel> createNodeView(final int viewIndex,
            final AugmentSampleWithBNNodeModel nodeModel) {
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
        return new AugmentSampleWithBNNodeDialog();

    }

}

