package ch.resear.thiriot.knime.bayesiannetworks.create.empty;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "XMLBIFBNReader" Node.
 * Read a Bayesian network from the XML BIF format
 *
 * @author Samuel Thiriot
 */
public class CreateEmptyNodeFactory 
        extends NodeFactory<CreateEmptyNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CreateEmptyNodeModel createNodeModel() {
        return new CreateEmptyNodeModel();
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
    public NodeView<CreateEmptyNodeModel> createNodeView(final int viewIndex,
            final CreateEmptyNodeModel nodeModel) {
       
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
        return new CreateEmptyNodeDialog();
    }

}

