package thmp.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.Multimap;

import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.SyntaxnetQuery;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.parse.ParseStructType;
import thmp.parse.ParseToWLTree.WLCommandWrapper;
import thmp.parse.Struct;
import thmp.parse.ThmP1AuxiliaryClass.StructTreeComparator;

/**
 * Test to ensure queries to syntaxnet 
 * @author yihed
 *
 */
public class TestSyntaxnetQuery {
		
	private static final Map<String, Integer> INPUT_MAP;
	private static final boolean WRITE_UNKNOWN_WORDS_TO_FILE = false;
	
	static{
		INPUT_MAP = new HashMap<String, Integer>();
		INPUT_MAP.put("there are fields in the class $\\mathbb{C}$ which are not finite modifications of rings", 2);
		INPUT_MAP.put("The interchange of two distant critical points of the surface diagram does not change the induced map on homology", 3);
	}
	
	private static void f(String input, int expectedCoincidingNum) throws AssertionError, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
		//SyntaxnetQuery syntaxnetQuery = new SyntaxnetQuery(input);
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		parseStateBuilder.setWriteUnknownWordsToFile(WRITE_UNKNOWN_WORDS_TO_FILE);
		ParseState parseState = parseStateBuilder.build();
		boolean isVerbose = true;
		ParseRun.parseInput(input, parseState, isVerbose);
		
		SyntaxnetQuery syntaxnetQuery = parseState.getRecentSyntaxnetQuery();
		StructTreeComparator structTreeComparator 
			= new StructTreeComparator(syntaxnetQuery.sentence().getTokenList(), parseState.noTexTokenStructAr());

		Method getCountMethod = StructTreeComparator.class.getDeclaredMethod("getStructTreeRelationCount", new Class[]{Struct.class});
		getCountMethod.setAccessible(true);
		
		Multimap<ParseStructType, WLCommandWrapper> wrapperMMap = parseState.getHeadParseStruct().getWLCommandWrapperMMap();
		
		for(Map.Entry<ParseStructType, WLCommandWrapper> entry : wrapperMMap.entries()){
			WLCommandWrapper wrapper = entry.getValue();
			Struct highestStruct = wrapper.highestStruct(); 			
			int count = (Integer)getCountMethod.invoke(structTreeComparator, new Object[]{highestStruct});
			System.out.println("TestSyntaxnetQuery - expectedCoincidingNum, count: " + expectedCoincidingNum + "  "+ count);
			if(expectedCoincidingNum != count){
				throw new AssertionError();
			}			
			break; //<-expand to inputs with multiple parseStructTypes in future
		}					
	}
	
	@Test
	public void test() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
		List<String> failedStringList = new ArrayList<String>();
		
		for(Map.Entry<String, Integer> entry : INPUT_MAP.entrySet()){
			try{
				f(entry.getKey(), entry.getValue());				
			}catch(AssertionError e){
				failedStringList.add(entry.getKey());
			}			
		}
		if(!failedStringList.isEmpty()){			
			String msg = "AssertionError thrown on" + failedStringList;
			throw new AssertionError(msg);
		}		
	}
	
}
