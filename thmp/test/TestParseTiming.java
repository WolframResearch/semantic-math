package thmp.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.ParseStructType;
import thmp.parse.ParseState.ParseStateBuilder;

/**
 * Test parsing times.
 * @author yihed
 *
 */
public class TestParseTiming {
	
	private static final boolean WRITE_UNKNOWN_WORDS_TO_FILE = false;
	private static final String BASE_INPUT = "$f$ is holomorphic on $D(0, r)$";
	//in ms
	private static long BASE_DURATION = 12; //base duration should be 10 ms
	private static boolean baseTestRunBool;
	private static final List<InputTimingRatioPair> INPUT_LIST;
	
	static class InputTimingRatioPair{
		String input;
		//ratio with respect to base
		double ratioWrtBase;
		InputTimingRatioPair(String input_, double ratioWrtBase_){
			this.input = input_;
			this.ratioWrtBase = ratioWrtBase_;
		}
		
		@Override
		public String toString(){
			return this.input + " " + this.ratioWrtBase;
		}
	}
	
	static{
		//initialize everything
		parseThm(BASE_INPUT);
		INPUT_LIST = new ArrayList<InputTimingRatioPair>();
		INPUT_LIST.add(new InputTimingRatioPair("$f$ is a function with radius of convergence $r$", 2.2));//20 ms
		INPUT_LIST.add(new InputTimingRatioPair("take derivative of log of f", 1.5));	//10 ms
		INPUT_LIST.add(new InputTimingRatioPair("$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$", 1.5)); //2 good??
		INPUT_LIST.add(new InputTimingRatioPair("The derivative of $f$ is $\\sum_j j $", 1));//1 good?
		
	}
	
	/*public static void main(String[] args){
		//System.out.println("");
		testStm5();
	}*/
	@Test(timeout=20)//2600
	public void baseTest(){
		baseLineParse();
	}

	/**
	 * 
	 */
	private void baseLineParse() {
		long startTime = System.currentTimeMillis();
		//System.out.println("start time: " + System.currentTimeMillis());
		parseThm(BASE_INPUT);
		long duration = System.currentTimeMillis() - startTime;
		System.out.println("Base duration: " + duration);
		BASE_DURATION = duration;
		baseTestRunBool = true;
	}
	
	@Test
	public void testTimeMain(){
		if(!baseTestRunBool){
			baseLineParse();
		}
		List<InputTimingRatioPair> failedTimingPairsList 
			= new ArrayList<InputTimingRatioPair>();
		for(InputTimingRatioPair pair : INPUT_LIST){
			double actualRatio = 1;
			try{
				long startTime = System.currentTimeMillis();
				parseThm(pair.input);
				long endTime = System.currentTimeMillis();
				actualRatio = (endTime-startTime)/((double)BASE_DURATION);
				System.out.println("DURATION " + (endTime - startTime));
				Assert.assertTrue(actualRatio < pair.ratioWrtBase);
			}catch(AssertionError e){
				failedTimingPairsList.add(new InputTimingRatioPair(pair.input, actualRatio));
			}
		}
		if(!failedTimingPairsList.isEmpty()){
			throw new AssertionError("Inputs with their ratios that failed timing test: " + failedTimingPairsList);
		}
	}
	
	/**
	 * @param thm
	 */
	private static void parseThm(String thm) {
		boolean isVerbose = false;
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		ParseState parseState = parseStateBuilder.build();
		parseStateBuilder.setWriteUnknownWordsToFile(WRITE_UNKNOWN_WORDS_TO_FILE);
		
		ParseRun.parseInput(thm, parseState, isVerbose);
	}

	
	public void testStm1(){
		//"$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$"
		String thm = "f is a function with radius of convergence r and finitely many roots";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.STM, "f \\[Element] function[Conj[[\"Property\"->\"function\", \"r\", \"Qualifiers\" -> {\"with\", \"Property\"->\"radius of convergence\"}], [\"Property\"->\"root\", \"finitely many\"]]]");
		//STM=[f \[Element] function[Conj[["Type"->"function", "r", "Qualifiers" -> {"with", "Type"->"radius of convergence"}], ["Type"->"root", "finitely many"]]]]}]
		
		//Math["Type"->"element", "$S$"]
		
	}
	
	//stList.add("f is a function with radius of convergence r and finitely many roots");
	//desiredMap5.put(ParseStructType.STM, "f \\[Element] function[{function, {with, Conj[{radius of convergence, r}, {roots, finitely many}]}}]");
	
	public void testHyp1(){
		String thm = "given an element f of a set $S$";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.HYP, "Math[\"Property\"->\"element\", \"$S$\"]");
		//Math["Type"->"element", "$S$"]
		
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
		
	}
}
