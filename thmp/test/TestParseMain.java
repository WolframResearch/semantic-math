package thmp.test;


import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import thmp.Maps;
import thmp.ThmP1;
import thmp.ThmP1.ParsedPair;
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
		List<String> parsedList = new ArrayList<String>();
		
		//the parsed results must be added in the same order as their inputs
		stList.add("let f be g");
		parsedList.add("{HYP=[ f \\[Element] MathObj{g}  0.7  4  3]}\n");
		stList.add("given an element f of a set $S$");
		parsedList.add("{HYP=[ MathObj{element, f, MathObj{set, $S$}}  1.0  2  4]}\n");
		stList.add("take derivative of log of f");
		parsedList.add("{OBJ=[ Derivative[  Log[ f ]  ]  1.0  1  6]}\n");
		stList.add("f is a function with radius of convergence r");
		parsedList.add("{STM=[ f \\[Element] function[{function, {with, radius of convergence, r}}]  1.0  3  6]}\n");
		stList.add("f is a function with radius of convergence r and finitely many roots");
		parsedList.add("{STM=[ f \\[Element] function[{function, {with, Conj[{radius of convergence, r}, {roots, finitely many}]}}]  1.0  3  7]}\n");
		stList.add("$f$ is holomorphic on $D(0, r)$, the derivative of $f$ is $\\sum_j j $");
		parsedList.add("{STM=[  Derivative[ $f$ ]  \\[Element] MathObj{{$\\sum_j j $}}  1.0  3  7]}\n");
		//parsedList.add("{STM=[  Derivative[ $f$ ]  \\[Element] MathObj{{$\\sum_j j}}  1.0  3  7]}\n");
		stList.add("$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$");
		parsedList.add("{STM=[ MathObj{$R/\\mathfrak p$} \\[Element] MathObj{catenary}  0.9  3  4], HYP=[ \\[ForAll][ MathObj{prime, $\\mathfrak p$, minimal} ]  1.0  2  3]}\n");
		
		for(int j = 0; j < stList.size(); j++){
			String st = stList.get(j);
			String[] strAr = ThmP1.preprocess(st);
			for(int i = 0; i < strAr.length; i++){
				//alternate commented out line to enable tex converter
				//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim()) ));
				ThmP1.parse(ThmP1.tokenize(strAr[i].trim()));				
			}
			List<String> parseStructMapList = ThmP1.getParseStructMapList();
			
			boolean parsePresent = parseStructMapList.contains(parsedList.get(j));
			assertTrue("Testing " + st, parsePresent);
			
			if(!parsePresent){
				System.out.println("Incorrect parse: " + st);
			}
			
			//System.out.println("*******"+parseStructMapList);
			//System.out.println("*******"+parsedList.get(j));
			
			//System.out.println("RESULT: "+Arrays.toString(parseStructMapList.toArray()) + "/RESULT");
			
		}	
	}

}
