package thmp.utils;

import java.util.List;

import thmp.parse.ParsedExpression;;

/**
 * Place to deposit resources, to be retrieved by various classes.
 * This should be deprecated -June 2017
 * @deprecated - June 2017. Maybe this can be useful in future
 * @author yihed
 *
 */
public class ResourceDeposit {

	private static List<ParsedExpression> parsedExpressionList;
	
	public static void setParsedExpressionList(List<ParsedExpression> parsedExpressionList_){
		if(null == parsedExpressionList){
			synchronized(ResourceDeposit.class){
				if(null == parsedExpressionList){
					parsedExpressionList = parsedExpressionList_;					
				}
			}			
		}
	}
	
	/*public static List<ParsedExpression> getParsedExpressionList(){
		return parsedExpressionList;
	}*/
}
