package thmp.utils;

import java.util.List;

import thmp.parse.ParsedExpression;;

/**
 * Place to deposit resources, to be retrieved by various classes.
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
	
	public static List<ParsedExpression> getParsedExpressionList(){
		return parsedExpressionList;
	}
}
