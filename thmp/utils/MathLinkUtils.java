package thmp.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

public class MathLinkUtils {

	private static final KernelLink ml = FileUtils.getKernelLinkInstance();
	//placeholder Expr to avoid using null.
	private static final Expr PLACEHOLDER_EXPR = new Expr("");
	private static final Logger logger = LogManager.getLogger(MathLinkUtils.class);
	
	public static Expr evaluateWLCommand(String cmd){
		return MathLinkUtils.evaluateWLCommand(cmd, false, false);
	}
	
	/**
	 * Executes WL command via JLink.
	 * @param cmd Should not contain semicolon at end.
	 * @param getResultingExpr
	 */
	public static Expr evaluateWLCommand(String cmd, boolean getResultingExpr, boolean throwOnException){
		
		int cmdLen = cmd.length();
		try{
			if(getResultingExpr){				
				if(cmd.charAt(cmdLen-1) == ';'){
					cmd = cmd.substring(0, cmdLen-1);
				}
				ml.evaluate(cmd);
				ml.waitForAnswer();
				return ml.getExpr();
			}else{
				if(cmd.charAt(cmd.length()-1) != ';'){
					cmd = cmd + ";";
				}
				ml.evaluate(cmd);
				ml.discardAnswer();
				return PLACEHOLDER_EXPR;
			}
		}catch(MathLinkException e){
			String msg = "";
			System.out.println(msg);
			logger.error(msg);
			if(throwOnException){
				throw new IllegalStateException(e);
			}else{
				return PLACEHOLDER_EXPR;				
			}
		}
	}
}
