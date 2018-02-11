package thmp.utils;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.kernelserver.expr.Expression;
import com.wolfram.webkernel.EvaluationException;
import com.wolfram.webkernel.IKernel;

public class MathLinkUtils {
	//to be used locally, not on server.
	//private static final KernelLink localMathLink = FileUtils.getKernelLinkInstance();
	//private static ServletContext servletContext;
	//placeholder Expr to avoid using null.
	private static final Expr PLACEHOLDER_EXPR = new Expr("");
	private static final Logger logger = LogManager.getLogger(MathLinkUtils.class);
	
	/*public static Expr evaluateWLCommand(String cmd){
		return MathLinkUtils.evaluateWLCommand(ml, cmd, false, true);
	}*/
	
	/*public static void setServletContext(ServletContext servletContext_){
		servletContext = servletContext_;
	}*/	
	/**
	 * Wrapper class for either link or kernel.
	 */
	public static class WLEvaluationMedium{
		
		private IKernel kernel;
		private KernelLink link;
		EvaluationMediumType evaluationMediumType;
		
		enum EvaluationMediumType{
			KERNEL,
			LINK;
		}
		
		public WLEvaluationMedium(IKernel kernel_){
			this.kernel = kernel_;
			this.evaluationMediumType = EvaluationMediumType.KERNEL;
		}
		
		public WLEvaluationMedium(KernelLink link_){
			this.link = link_;
			this.evaluationMediumType = EvaluationMediumType.LINK;
		}
		
		public IKernel kernel(){
			return this.kernel;
		}
		
		public KernelLink link(){
			return this.link;
		}
	}
	
	public static Expr evaluateWLCommand(WLEvaluationMedium medium, String cmd){
		return MathLinkUtils.evaluateWLCommand(medium, cmd, false, true);
	}
	
	/*public static Expr evaluateWLCommand(String cmd, boolean getResultingExpr, boolean throwOnException){
		return evaluateWLCommand(ml, cmd, getResultingExpr, throwOnException);
	}*/
	
	/**
	 * Executes WL command via JLink.
	 * 
	 * @param medium Medium wrapping kernel
	 * @param cmd Should not contain semicolon at end.
	 * @param getResultingExpr
	 * @param throwOnException
	 */
	public static Expr evaluateWLCommand(WLEvaluationMedium medium, String cmd, boolean getResultingExpr, boolean throwOnException){
		
		switch(medium.evaluationMediumType){
		case KERNEL:
			return evaluateWLCommandWithKernel(medium.kernel(), cmd, getResultingExpr, throwOnException);
		case LINK:
			return evaluateWLCommandWithLink(medium.link(), cmd, getResultingExpr, throwOnException);
		default:
			assert false : "evaluationMediumType cannot be this case";
			return PLACEHOLDER_EXPR;
		}
	}
	
	private static Expr evaluateWLCommandWithKernel(IKernel kernel, String cmd, boolean getResultingExpr, boolean throwOnException){
		int cmdLen = cmd.length();
		try {
			if(getResultingExpr){
				if(cmd.charAt(cmdLen-1) == ';'){
					cmd = cmd.substring(0, cmdLen-1);
				}
				//kernel.evaluate() calls kernel.eval(), which waits.
				Expression expression = kernel.evaluate(cmd);
				return expression.getExpr();	
			}else{
				if(cmd.charAt(cmd.length()-1) != ';'){
					cmd = cmd + ";";
				}
				//kernel.evaluate() calls kernel.eval(), which waits.
				kernel.evaluate(cmd);
				return PLACEHOLDER_EXPR;
			}
		} catch (EvaluationException e) {
			if(throwOnException){
				throw new IllegalStateException(e);
			}else{
				//don't have another possibly better expression to input anyway.
				String msg = "EvaluationException in evaluateWLCommandWithKernel() on command "
						+ cmd + " with error message " 
						+ Arrays.toString(e.getStackTrace());
				logger.error(msg);
				return PLACEHOLDER_EXPR;
			}			
		}		
	}
	
	private static Expr evaluateWLCommandWithLink(KernelLink ml, String cmd, boolean getResultingExpr, boolean throwOnException){
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
			String msg = "MathLinkException when evaluating: " + cmd;
			//System.out.println(msg);
			logger.error(msg);
			if(true){
			//if(throwOnException){
				throw new IllegalStateException(e);
			}else{
				return PLACEHOLDER_EXPR;				
			}
		}
	}
}
