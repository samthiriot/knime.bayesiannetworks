package ch.resear.thiriot.knime.bayesiannetworks.lib;

public interface ILogger {

	boolean isDebugEnabled();

	void debug(String string);

	boolean isInfoEnabled();

	void info(String string);

	void warn(String string);

	void error(String string);

}
