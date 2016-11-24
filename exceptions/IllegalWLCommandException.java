package exceptions;

/**
 * Exception when illegal WLCommand is constructed.
 * @author yihed
 *
 */
public class IllegalWLCommandException extends Exception{

	private static final long serialVersionUID = 1L;
	
	public IllegalWLCommandException(){
		super();
	}
	
	public IllegalWLCommandException(String msg){
		super(msg);
	}
	
	public IllegalWLCommandException(Throwable cause){
		super(cause);
	}
	
	public IllegalWLCommandException(String msg, Throwable cause){
		super(msg, cause);
	}
}
