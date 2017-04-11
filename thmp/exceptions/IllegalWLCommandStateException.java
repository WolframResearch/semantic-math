package thmp.exceptions;

/**
 * Illegal WLCommand state, such as inconsistency during building.
 * Used to short-circuit the command building.
 * @author yihed
 *
 */
public class IllegalWLCommandStateException extends Exception{

	private static final long serialVersionUID = -7887030339571251652L;
	
	public IllegalWLCommandStateException(){
		super();
	}
	
	public IllegalWLCommandStateException(String msg){
		super(msg);
	}
	
	public IllegalWLCommandStateException(Throwable cause){
		super(cause);
	}
	
	public IllegalWLCommandStateException(String msg, Throwable cause){
		super(msg, cause);
	}
	
}
