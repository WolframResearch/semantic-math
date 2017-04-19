package thmp.test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.ParseState;
import thmp.ParseStructType;
import thmp.ThmP1;
import thmp.exceptions.ParseRuntimeException.IllegalSyntaxException;
import thmp.test.ParseEqualityCheck.ParseResult;
import thmp.utils.FileUtils;
import thmp.ParseState.ParseStateBuilder;

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
	
	@Before
	public void setUp() throws Exception {
		/*Maps.buildMap();
		try {
			Maps.readLexicon();
			Maps.readFixedPhrases();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}*/
		ThmP1.setUnitTestingToTrue();		
	}

	/**
	 * ADD TO TESTS:
	 * "5;"
	 */
	
	
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
	
	
	public void testStm5(){
		String thm = "f is a function with radius of convergence r";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		//This is the wrong parse!
		desiredMap.put(ParseStructType.STM, "f \\[Element] function[\"Property\"->\"function\", \"r\", \"Qualifiers\" -> {\"with\", \"Property\"->\"radius of convergence\"}]");
		parseThm(thm, desiredMap);
	}
	
	
	public void testStm4(){
		String thm = "take derivative of log of f";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		//This is the wrong parse!
		desiredMap.put(ParseStructType.OBJ, "Derivative[ Log[ f ]  ]");
		parseThm(thm, desiredMap);
	}

	
	public void testStm3(){
		String thm = "$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		//This is the wrong parse!
		desiredMap.put(ParseStructType.STM, "Math[\"Property\"->\"$R/\\mathfrak p$\"] ~HasProperty~ ring[catenary]");
		desiredMap.put(ParseStructType.HYP, "\\[ForAll][ Math[\"Property\"->\"prime\", \"$\\mathfrak p$\", \"minimal\"] ]");
		//Math["Type"->"element", "$S$"]
		parseThm(thm, desiredMap);
	}	
	
	public void testStm2(){
		String thm = "$f$ is holomorphic on $D(0, r)$";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.STM, "$f$ ~HasProperty~ PowerSeries[holomorphic] , {Qualifier-> [on, Math[\"Property\"->\"$D(0 , r)$\"]] }");
		//could also be "STM=[$f$ \[Element] PowerSeries[[holomorphic, [on, "Type"->"$D(0 , r)$"]]]]"
		//Derivative[ $f$ ]  \[Element] Math["Type"->"$\sum_j j $"]
		//Math["Type"->"element", "$S$"]
		parseThm(thm, desiredMap);
	}
	
	
	public void testStm2_1(){
		String thm = "The derivative of $f$ is $\\sum_j j $";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.STM, "Derivative[ $f$ ]  \\[Element] Math[\"Property\"->\"$\\sum_j j $\"]");
		//Derivative[ $f$ ]  \[Element] Math["Type"->"$\sum_j j $"]
		//Math["Type"->"element", "$S$"]
		parseThm(thm, desiredMap);
	}
	//stList.add("$f$ is holomorphic on $D(0, r)$, the derivative of $f$ is $\\sum_j j $");
	//Multimap<ParseStructType, String> desiredMap6 = ArrayListMultimap.create();
	//desiredMap6.put(ParseStructType.STM, "Derivative[ $f$ ]  \\[Element] MathObj{{$\\sum_j j $}}");
	//parsedMMapList.add(desiredMap6);
	
	
	public void testStm1(){
		//"$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$"
		String thm = "f is a function with radius of convergence r and finitely many roots";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.STM, "f \\[Element] function[Conj[[\"Property\"->\"function\", \"r\", \"Qualifiers\" -> {\"with\", \"Property\"->\"radius of convergence\"}], [\"Property\"->\"root\", \"finitely many\"]]]");
		//STM=[f \[Element] function[Conj[["Type"->"function", "r", "Qualifiers" -> {"with", "Type"->"radius of convergence"}], ["Type"->"root", "finitely many"]]]]}]
		
		//Math["Type"->"element", "$S$"]
		parseThm(thm, desiredMap);
	}
	
	//stList.add("f is a function with radius of convergence r and finitely many roots");
	//desiredMap5.put(ParseStructType.STM, "f \\[Element] function[{function, {with, Conj[{radius of convergence, r}, {roots, finitely many}]}}]");
	
	
	public void testHyp1(){
		String thm = "given an element f of a set $S$";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.HYP, "Math[\"Property\"->\"element\", \"$S$\"]");
		//Math["Type"->"element", "$S$"]
		parseThm(thm, desiredMap);
	}

	/**
	 * Test $R_\\mathfrak m$ is universally catenary for all maximal ideals $\\mathfrak m$
	 */
	
	public void test2(){
		String thm = "$R_\\mathfrak m$ is universally catenary for all maximal ideals $\\mathfrak m$";
		//String parsed = "{HYP=[ \\[ForAll][ MathObj{maximal ideal, $\\mathfrak m$} ]  1.0  2  3], STM=[ MathObj{$R_\\mathfrak m$} \\[Element] MathObj{universally catenary}  0.9  3  4]}\n";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.HYP, "\\[ForAll][ Math[\"Property\"->\"maximal ideal\", \"$\\mathfrak m$\"] ]");
		//\[ForAll][ Math["Type"->"maximal ideal", "$\mathfrak m$"] ]
		desiredMap.put(ParseStructType.STM, "Math[\"Property\"->\"$R_\\mathfrak m$\"] ~HasProperty~ Math[universally catenary]");
		parseThm(thm, desiredMap);
	}
	
	/**
	 * Test $M/gM$ is Cohen-Macaulay with maximal regular sequence $f_1, \\ldots, f_{d-1}$.
	 */
	
	public void test3(){
		String thm = "$M/gM$ is Cohen-Macaulay with maximal regular sequence $f_1, \\ldots, f_{d-1}$.";
		//String parsed = "{STM=[ MathObj{$M/gM$} \\[Element] ring[{cohen-macaulay, {with, sequence, $f_1, \\ldots, f_{d-1}$, maximal regular}}]  0.85  5  6]}\n";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.STM, 
				"Math[\"Name\"->\"$M/gM$\"] \\[Element] ring[[cohen-macaulay, [with, \"Name\"->\"sequence\", \"$f_1 , \\ldots , f_{d-1}$\", \"Property\"->\"maximal regular\"]]]");
		parseThm(thm, desiredMap);
	}
	
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
		//assertTrue(ParseEqualityCheck.checkParse(parseResultsList.get(7)));
	}
	
	private static List<ParseResult> deserializeParseResults(){
		String serialFileStr = thmp.utils.SerializeParseResult.parseResultSerialFile;
		
		@SuppressWarnings("unchecked")
		List<ParseResult> parseResultList = (List<ParseResult>)FileUtils.deserializeListFromFile(serialFileStr);
		System.out.println("parseResultList " +parseResultList);
		return parseResultList;
	}
	
	
}
