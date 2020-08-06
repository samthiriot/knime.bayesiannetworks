package ch.resear.thiriot.knime.bayesiannetworks.learn;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;

/**
 * <code>NodeFactory</code> for the "LearnBNFromSample" Node.
 * For a given Bayesian network, learns the conditional probabilities from a given sample
 *
 * @author Samuel Thiriot
 */
public class LearnBNFromSampleNodeFactory 
        extends NodeFactory<LearnBNFromSampleNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public LearnBNFromSampleNodeModel createNodeModel() {
        return new LearnBNFromSampleNodeModel();
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
    public NodeView<LearnBNFromSampleNodeModel> createNodeView(final int viewIndex,
            final LearnBNFromSampleNodeModel nodeModel) {
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
        return new DefaultNodeSettingsPane();
    }

}

