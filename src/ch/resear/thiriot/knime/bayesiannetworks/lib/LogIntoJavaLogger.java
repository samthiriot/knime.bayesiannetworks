package ch.resear.thiriot.knime.bayesiannetworks.lib;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogIntoJavaLogger implements ILogger {

	public final Logger l;
	
	public static LogIntoJavaLogger getLogger(String name) {
		return new LogIntoJavaLogger(Logger.getLogger(name));
	}
	
	public static LogIntoJavaLogger getLogger(Class<?> c) {
		return new LogIntoJavaLogger(Logger.getLogger(c.getName()));
	}
	
	public LogIntoJavaLogger(Logger logger) {
		this.l = logger;
	}
	
	@Override
	public boolean isDebugEnabled() {
		return l.isLoggable(Level.FINE);
	}

	@Override
	public void debug(String string) {
		l.fine(string);
	}

	@Override
	public boolean isInfoEnabled() {
		return l.isLoggable(Level.INFO);
	}

	@Override
	public void info(String string) {
		l.info(string);
	}

	@Override
	public void warn(String string) {
		l.warning(string);
	}

	@Override
	public void error(String string) {
		l.severe(string);
	}

}
