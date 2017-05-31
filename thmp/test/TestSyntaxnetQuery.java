package thmp.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import thmp.exceptions.ParseRuntimeException.IllegalSyntaxException;
import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.SyntaxnetQuery;
import thmp.parse.ThmP1;
import thmp.parse.ParseState.ParseStateBuilder;
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
		INPUT_MAP.put("there are fields in the class $\\mathbb{C}$ which are not finite modifications of rings", 1);
		INPUT_MAP.put("The interchange of two distant critical points of the surface diagram does not change the induced map on homology", 2);
	}
	
	private static void f(String input, int expectedCoincidingNum) throws AssertionError, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
		SyntaxnetQuery syntaxnetQuery = new SyntaxnetQuery(input);
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		parseStateBuilder.setWriteUnknownWordsToFile(WRITE_UNKNOWN_WORDS_TO_FILE);
		ParseState parseState = parseStateBuilder.build();
		//boolean isVerbose = true;
		//ParseRun.parseInput(input, parseState, isVerbose);
		
		try {
			parseState = ThmP1.tokenize(input, parseState);
		} catch (IllegalSyntaxException e) {
			System.out.println("Input contains illegal syntax!");
			e.printStackTrace();
			return;
		}		
		parseState = ThmP1.parse(parseState);
		
		parseState.getHeadParseStruct().struc;
		
		StructTreeComparator structTreeComparator 
			= new StructTreeComparator(syntaxnetQuery.sentence().getTokenList(), parseState.noTexTokenStructAr());
		
		//Method methods = StructTreeComparator.class.getMethod("", new Class[]{});
		Method getCountMethod = StructTreeComparator.class.getDeclaredMethod("getStructTreeRelationCount", new Class[]{thmp.parse.Struct.class});
		getCountMethod.setAccessible(true);
		
		int count = (Integer)getCountMethod.invoke(structTreeComparator, new Object[]{});
		if(expectedCoincidingNum != count){
			throw new AssertionError();
		}		
	}
	
	@Test
	public void test(){
		List<String> failedStringList = new ArrayList<String>();
		
		for(Map.Entry<String, Integer> entry : INPUT_MAP.entrySet()){
			try{
				f(entry.getKey(), entry.getValue());
				
			}catch(AssertionError e){
				failedStringList.add(entry.getKey());
			}			
		}
		if(!failedStringList.isEmpty()){
			
		}		
	}
	
}
