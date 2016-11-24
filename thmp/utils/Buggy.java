package thmp.utils;

import org.apache.logging.log4j.*;

/**
 * Tools to facilitate debugging.
 * e.g. singleton logger instance.
 * @author yihed
 *
 */
public class Buggy {

	private static final Logger logger = LogManager.getLogger();
	
	public static Logger getLogger(){
		return logger;
	}
}
