package thmp.test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.exceptions.ParseRuntimeException.IllegalSyntaxException;
import thmp.parse.ParseState;
import thmp.parse.ParseStructType;
import thmp.parse.ThmP1;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.test.ParseEqualityCheck.ParseResult;
import thmp.utils.FileUtils;

/**
 * Parses preliminary set of Strings.
 * @author yihed
 *
 */
public class TestParseMain {

	private static final boolean WRITE_UNKNOWN_WORDS_TO_FILE = false;
	private static final List<ParseResult> parseResultsList;
	private static final List<String> assertionErrorInputStrList = new ArrayList<String>();
	
	static{
		parseResultsList = deserializeParseResults();
	}
	
	@BeforeClass
	public static void setUp() throws Exception {
		/*Maps.buildMap();
		try {
			Maps.readLexicon();
			Maps.readFixedPhrases();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}*/
		ThmP1.setUnitTestingToTrue();		
	}
	
	public void basicTests() throws IOException{
		List<String> stList = new ArrayList<String>();
		//list of expected parses to compare output to
		List<Multimap<ParseStructType, String>> parsedMMapList = new ArrayList<Multimap<ParseStructType, String>>();
		
		//the parsed results must be added in the same order as their inputs
		stList.add("let f be g");
		Multimap<ParseStructType, String> desiredMap1 = ArrayListMultimap.create();
		desiredMap1.put(ParseStructType.HYP, "f \\[Element] Math[g]");
		parsedMMapList.add(desiredMap1);
		//keeping these comments for comparing scores
		//parsedList.add("{HYP=[ f \\[Element] MathObj{g}  0.7  4  3]}\n");
		
		stList.add("quotient over ring is quotient");
		Multimap<ParseStructType, String> desiredMap8 = ArrayListMultimap.create();
		desiredMap8.put(ParseStructType.STM, "Math[\"Name\"->\"quotient\", \"Qualifiers\" -> {\"over\", Math[\"Name\"->\"ring\"]}] \\[Element] Math[\"Name\"->\"quotient\"]");
		parsedMMapList.add(desiredMap8);
		//parsedList.add("{STM=[ MathObj{quotient, over MathObj{ring}} \\[Element] MathObj{{quotient}}  1.0  3  6]}\n");
		
		for(int j = 0; j < stList.size(); j++){
			String str = stList.get(j);
			Multimap<ParseStructType, String> map = parsedMMapList.get(j);
			parseThm(str, map);					
		}	
	}

	/**
	 * @param st Theorem to be parsed
	 * @param parsedSt Desired parse result
	 * @throws IOException
	 */
	private void parseThm(String st, Multimap<ParseStructType, String> desiredParseMap) {
		
		String[] strAr = ThmP1.preprocess(st);
		
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		parseStateBuilder.setWriteUnknownWordsToFile(WRITE_UNKNOWN_WORDS_TO_FILE);
		ParseState parseState = parseStateBuilder.build();
		
		for(int i = 0; i < strAr.length; i++){
			//alternate commented out line to enable tex converter
			//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim()) ));
			try {
				parseState = ThmP1.tokenize(strAr[i].trim(), parseState);
			} catch (IllegalSyntaxException e) {
				e.printStackTrace();
				continue;
			}
			parseState = ThmP1.parse(parseState);
		}
	
		System.out.println("@@@" + parseState.getHeadParseStruct());
		//List<String> parseStructMapList = ThmP1.getParseStructMapList();
		
		List<Multimap<ParseStructType, String>> parseStructMapsList = ThmP1.getAndClearParseStructMapsForTesting();		
		
		boolean parsePresent = parseStructMapsList.contains(desiredParseMap);
		//boolean parsePresent = parseStructMapList.contains(parsedSt);		

		if(!parsePresent){
			System.out.println("Desired Map: " + desiredParseMap + 
					"\nActual parseStructMapsList: " + parseStructMapsList);
		}		
		assertTrue("Testing " + st, parsePresent);		
	}
	///
	/*stList.add("$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$");
	Multimap<ParseStructType, String> desiredMap7 = ArrayListMultimap.create();
	desiredMap7.put(ParseStructType.HYP, "\\[ForAll][ MathObj{minimal prime, $\\mathfrak p$} ]");
	desiredMap7.put(ParseStructType.STM, "MathObj{$R/\\mathfrak p$} \\[Element] MathObj{catenary}");
	parsedMMapList.add(desiredMap7);*/	
	
	@Test
	public void test10(){
		for(ParseResult pr : parseResultsList){
			try{
				boolean s = ParseEqualityCheck.checkParse(pr);
				assertTrue(s);
			}catch(AssertionError e){	
				e.printStackTrace();
				assertionErrorInputStrList.add(pr.inputString());
			}
		}
		if(!assertionErrorInputStrList.isEmpty()){
			String msg = "Tests did not pass! List of inputs that failed: " + assertionErrorInputStrList;
			System.out.println(msg);
			throw new AssertionError(msg);
		}
	}
	
	private static List<ParseResult> deserializeParseResults(){
		String serialFileStr = thmp.utils.SerializeParseResult.parseResultSerialFile;
		
		@SuppressWarnings("unchecked")
		List<ParseResult> parseResultList = (List<ParseResult>)FileUtils.deserializeListFromFile(serialFileStr);
		//System.out.println("parseResultList " +parseResultList); //encounter null Expr in Rule Expr, cause did not make Expr serializable
		return parseResultList;
	}
	
}
