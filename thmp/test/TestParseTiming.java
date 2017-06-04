package thmp.test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.parse.ParseStructType;

/**
 * Test parsing times.
 * @author yihed
 *
 */
public class TestParseTiming {


	
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
		//parseThm(thm, desiredMap);
		
	}
	
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
}
