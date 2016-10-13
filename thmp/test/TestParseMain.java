package thmp.test;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.Maps;
import thmp.ParseStructType;
import thmp.ThmP1;

/**
 * Parses preliminary set of Strings.
 * @author yihed
 *
 */
public class TestParseMain {

	@Before
	public void setUp() throws Exception {
		Maps.buildMap();
		try {
			Maps.readLexicon();
			Maps.readFixedPhrases();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void test() throws IOException{
		List<String> stList = new ArrayList<String>();
		//list of expected parses to compare output to
		List<Multimap<ParseStructType, String>> parsedList = new ArrayList<Multimap<ParseStructType, String>>();
		
		//the parsed results must be added in the same order as their inputs
		stList.add("let f be g");
		Multimap<ParseStructType, String> desiredMap1 = ArrayListMultimap.create();
		desiredMap1.put(ParseStructType.HYP, "f \\[Element] MathObj{g}");
		parsedList.add(desiredMap1);
		//keeping these comments for comparing scores
		//parsedList.add("{HYP=[ f \\[Element] MathObj{g}  0.7  4  3]}\n");
		
		stList.add("given an element f of a set $S$");
		Multimap<ParseStructType, String> desiredMap2 = ArrayListMultimap.create();
		desiredMap1.put(ParseStructType.HYP, "MathObj{element, f, of MathObj{set, $S$}}");
		parsedList.add(desiredMap2);		
		//parsedList.add("{HYP=[ MathObj{element, f, of MathObj{set, $S$}}  1.0  2  4]}\n");
		
		stList.add("take derivative of log of f");
		Multimap<ParseStructType, String> desiredMap3 = ArrayListMultimap.create();
		desiredMap3.put(ParseStructType.OBJ, "Derivative[  Log[ f ]  ]");
		parsedList.add(desiredMap3);		
		//parsedList.add("{OBJ=[ Derivative[  Log[ f ]  ]  1.0  1  6]}\n");
		
		stList.add("f is a function with radius of convergence r");
		Multimap<ParseStructType, String> desiredMap4 = ArrayListMultimap.create();
		desiredMap1.put(ParseStructType.STM, "f \\[Element] function[{function, with radius of convergence, r}]");
		parsedList.add(desiredMap4);
		//parsedList.add("{STM=[ f \\[Element] function[{function, with radius of convergence, r}]  1.0  3  5]}\n");
		
		stList.add("f is a function with radius of convergence r and finitely many roots");
		Multimap<ParseStructType, String> desiredMap5 = ArrayListMultimap.create();
		desiredMap1.put(ParseStructType.STM, "f \\[Element] function[{function, {with, Conj[{radius of convergence, r}, {roots, finitely many}]}}]");
		parsedList.add(desiredMap5);
		//parsedList.add("{STM=[ f \\[Element] function[{function, {with, Conj[{radius of convergence, r}, {roots, finitely many}]}}]  1.0  3  7]}\n");
		
		stList.add("$f$ is holomorphic on $D(0, r)$, the derivative of $f$ is $\\sum_j j $");
		Multimap<ParseStructType, String> desiredMap6 = ArrayListMultimap.create();
		desiredMap1.put(ParseStructType.STM, "Derivative[ $f$ ]  \\[Element] MathObj{{$\\sum_j j $}}");
		parsedList.add(desiredMap6);
		//parsedList.add("{STM=[  Derivative[ $f$ ]  \\[Element] MathObj{{$\\sum_j j $}}  1.0  3  7]}\n");
		
		stList.add("$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$");
		Multimap<ParseStructType, String> desiredMap7 = ArrayListMultimap.create();
		desiredMap1.put(ParseStructType.HYP, "\\[ForAll][ MathObj{minimal prime, $\\mathfrak p$} ]");
		desiredMap1.put(ParseStructType.STM, "MathObj{$R/\\mathfrak p$} \\[Element] MathObj{catenary}");
		parsedList.add(desiredMap7);
		//parsedList.add("{HYP=[ \\[ForAll][ MathObj{minimal prime, $\\mathfrak p$} ]  1.0  2  3], STM=[ MathObj{$R/\\mathfrak p$} \\[Element] MathObj{catenary}  0.9  3  4]}\n");
		              //{HYP=[ \\[ForAll][ MathObj{minimal prime, $\\mathfrak p$} ]  1.0  2  3], STM=[ MathObj{$R/\mathfrak p$} \[Element] MathObj{catenary}  0.9  3  4]}
		
		stList.add("quotient over ring is quotient");
		Multimap<ParseStructType, String> desiredMap8 = ArrayListMultimap.create();
		desiredMap1.put(ParseStructType.STM, "MathObj{quotient, over MathObj{ring}} \\[Element] MathObj{{quotient}}");
		parsedList.add(desiredMap8);
		//parsedList.add("{STM=[ MathObj{quotient, over MathObj{ring}} \\[Element] MathObj{{quotient}}  1.0  3  6]}\n");
		
		for(int j = 0; j < stList.size(); j++){
			 String str = stList.get(j);
			 Multimap<ParseStructType, String> map = parsedList.get(j);
			parseThm(str, map);
			
			//System.out.println("*******"+parseStructMapList);
			//System.out.println("*******"+parsedList.get(j));
			
			//System.out.println("RESULT: "+Arrays.toString(parseStructMapList.toArray()) + "/RESULT");			
		}	
	}

	/**
	 * @param st Theorem to be parsed
	 * @param parsedSt Desired parse result
	 * @throws IOException
	 */
	private void parseThm(String st, Multimap<ParseStructType, String> desiredParseMap) {
		String[] strAr = ThmP1.preprocess(st);
		
		for(int i = 0; i < strAr.length; i++){
			//alternate commented out line to enable tex converter
				//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim()) ));
			ThmP1.parse(ThmP1.tokenize(strAr[i].trim()));
		}
	
		//List<String> parseStructMapList = ThmP1.getParseStructMapList();
		
		List<Multimap<ParseStructType, String>> parseStructMaps = ThmP1.getParseStructMaps();		
		
		boolean parsePresent = parseStructMaps.contains(desiredParseMap);
		//boolean parsePresent = parseStructMapList.contains(parsedSt);
		
		assertTrue("Testing " + st, parsePresent);
		
		if(!parsePresent){
			System.out.println("Incorrect parse: " + st);
		}
	}
	
	/**
	 * Test $R_\\mathfrak m$ is universally catenary for all maximal ideals $\\mathfrak m$
	 */
	@Test
	public void test2(){
		//           "$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$"
		String thm = "$R_\\mathfrak m$ is universally catenary for all maximal ideals $\\mathfrak m$";
		//String parsed = "{HYP=[ \\[ForAll][ MathObj{maximal ideal, $\\mathfrak m$} ]  1.0  2  3], STM=[ MathObj{$R_\\mathfrak m$} \\[Element] MathObj{universally catenary}  0.9  3  4]}\n";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.HYP, "\\[ForAll][ MathObj{maximal ideal, $\\mathfrak m$} ]");
		desiredMap.put(ParseStructType.STM, "MathObj{$R_\\mathfrak m$} \\[Element] MathObj{universally catenary}");
		parseThm(thm, desiredMap);
	}
	
	/**
	 * Test $M/gM$ is Cohen-Macaulay with maximal regular sequence $f_1, \\ldots, f_{d-1}$.
	 */
	@Test
	public void test3(){
		//           "$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$"
		String thm = "$M/gM$ is Cohen-Macaulay with maximal regular sequence $f_1, \\ldots, f_{d-1}$.";
		//String parsed = "{STM=[ MathObj{$M/gM$} \\[Element] ring[{cohen-macaulay, {with, sequence, $f_1, \\ldots, f_{d-1}$, maximal regular}}]  0.85  5  6]}\n";
		
		Multimap<ParseStructType, String> desiredMap = ArrayListMultimap.create();
		desiredMap.put(ParseStructType.STM, "MathObj{$M/gM$} \\[Element] ring[{cohen-macaulay, {with, sequence, $f_1, \\ldots, f_{d-1}$, maximal regular}}]");
		parseThm(thm, desiredMap);
	}
	
}
