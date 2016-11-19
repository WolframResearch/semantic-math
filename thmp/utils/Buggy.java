package thmp.utils;

import java.util.logging.Level;

import org.apache.logging.log4j.*;
import org.apache.logging.log4j.simple.*;

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
