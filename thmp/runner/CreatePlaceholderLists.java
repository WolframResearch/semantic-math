package thmp.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import thmp.parse.ParseStruct;
import thmp.parse.ParsedExpression;
import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.utils.FileUtils;

/**
 * Creates placeholder lists, such as placeholder ParsedExpression lists.
 * 
 * @author yihed
 */
public class CreatePlaceholderLists {

	private static final String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
	
	public static void main(String[] args){
		
		serializeParsedExpressionsList();
		
	}
	
	private static void serializeParsedExpressionsList(){
		List<ParsedExpression> parsedExpressionList = new ArrayList<ParsedExpression>();
		parsedExpressionList.add(new ParsedExpression("this is a thm", new ParseStruct(), 
				new DefinitionListWithThm("this is a thm", Collections.emptyList(), "str", "file")));
		
		FileUtils.serializeObjToFile(parsedExpressionList, parsedExpressionSerialFileStr);
	}
	
}
