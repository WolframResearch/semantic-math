package thmp.exceptions;

import java.io.Serializable;

public class ParseRuntimeException extends Exception implements Serializable {

	private static final long serialVersionUID = 1579294934268979697L;

	public ParseRuntimeException(){
		super();
	}
	
	public ParseRuntimeException(String msg){
		super(msg);
	}
	
	public ParseRuntimeException(Throwable cause){
		super(cause);
	}
	
	public ParseRuntimeException(String msg, Throwable cause){
		super(msg, cause);
	}
	
	/**
	 * Used when user input contains invalid syntax.
	 */
	public static class IllegalSyntaxException extends ParseRuntimeException{

		private static final long serialVersionUID = 4200816726859502091L;
		
		public IllegalSyntaxException(){
			super();
		}
		public IllegalSyntaxException(String msg){
			super(msg);
		}
		
		public IllegalSyntaxException(Throwable cause){
			super(cause);
		}
		
		public IllegalSyntaxException(String msg, Throwable cause){
			super(msg, cause);
		}
	}
}
