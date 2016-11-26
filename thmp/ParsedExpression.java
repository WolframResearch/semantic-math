package thmp;

import java.io.Serializable;
import java.util.List;

/**
 * An instance of ParsedExpression represents a final parsed theorem.
 * Containing its top-scoring spanning parse trees (if any), 
 * the commands satisfied and built, 
 * @author yihed
 *
 */
public class ParsedExpression implements Serializable{

	private static final long serialVersionUID = 1L;
	
	private final String originalThmStr;
	
	private final List<Struct> parseRootList;
	
	
	public ParsedExpression(String thmStr, List<Struct> parseRootList){
		this.originalThmStr = thmStr;
		this.parseRootList = parseRootList;
	}
	
	
	
}
