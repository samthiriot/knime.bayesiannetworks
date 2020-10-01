package ch.resear.thiriot.knime.bayesiannetworks.enumerate;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "SampleFromBNNode" Node.
 * Using a Bayesian network which describes densities of probabilities, this node generates a population of entities (data table) with columns corresponding the variables of the BN.
 *
 * @author Samuel Thiriot
 */
public class EnumerateBNNodeFactory 
        extends NodeFactory<EnumerateBNNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public EnumerateBNNodeModel createNodeModel() {
        return new EnumerateBNNodeModel();
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
    public NodeView<EnumerateBNNodeModel> createNodeView(final int viewIndex,
            final EnumerateBNNodeModel nodeModel) {
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
        return new EnumerateBNNodeDialog();
    }

}

