package ch.resear.thiriot.knime.bayesiannetworks;

import org.knime.core.node.NodeLogger;

import ch.resear.thiriot.knime.bayesiannetworks.lib.ILogger;

/**
 * Enables the usage of KNIME NodeLogger to log the messages of the inference library.
 * Simple delegation!
 * 
 * @author Samuel Thiriot
 *
 */
public class LogIntoNodeLogger implements ILogger {

	final NodeLogger l;
	
	public LogIntoNodeLogger(NodeLogger logger) {
		this.l = logger;
	}
	
	@Override
	public boolean isDebugEnabled() {
		return l.isDebugEnabled();
	}

	@Override
	public void debug(String string) {
		l.debug(string);
	}

	@Override
	public boolean isInfoEnabled() {
		return l.isInfoEnabled();
	}

	@Override
	public void info(String string) {
		l.info(string);
	}

	@Override
	public void warn(String string) {
		l.warn(string);
	}

	@Override
	public void error(String string) {
		l.error(string);
	}

}
