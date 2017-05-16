package thmp.utils;

import java.util.ArrayList;
import java.util.List;

import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.ParseStruct;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.test.ParseEqualityCheck.ParseResult;

/**
 * Serialize parse result. Used for testing for now.
 * 
 * @author yihed
 */
public class SerializeParseResult {

	public static String parseResultSerialFile = "src/thmp/parseResultSerialFile.dat";
	
	public static void f(){
		
	}
	
	public static void main(String args[]){
		List<String> inputStrList = new ArrayList<String>();
		addInputToList(inputStrList);
		List<ParseResult> parseResultList = new ArrayList<ParseResult>();
		
		boolean isVerbose = false;
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();		
		ParseState parseState = parseStateBuilder.build();
		
		for(String inputString : inputStrList){
			ParseRun.parseInput(inputString, parseState, isVerbose);
			ParseStruct headParseStruct = parseState.getHeadParseStruct();
			System.out.println("headParseStruct " + headParseStruct);
			ParseResult pr = new ParseResult(inputString, headParseStruct);
			parseResultList.add(pr);
			parseState.parseRunGlobalCleanUp();
		}
		FileUtils.serializeObjToFile(parseResultList, parseResultSerialFile);
	}

	/**
	 * @param inputStrList
	 */
	private static void addInputToList(List<String> inputStrList) {
		inputStrList.add("take derivative of log of $f$");
		inputStrList.add("$M/gM$ is Cohen-Macaulay with maximal regular sequence $f_1, \\ldots, f_{d-1}$.");//
		inputStrList.add("$R_\\mathfrak m$ is universally catenary for all maximal ideals $\\mathfrak m$");//
		inputStrList.add("given an element f of a set $S$");
		inputStrList.add("f is a function with radius of convergence r and finitely many roots");
		inputStrList.add("The derivative of $f$ is $\\sum_j j $");
		inputStrList.add("$f$ is holomorphic on $D(0, r)$");
		inputStrList.add("$R/\\mathfrak p$ is catenary for every minimal prime $\\mathfrak p$");
		inputStrList.add("quotient over ring is quotient");
		inputStrList.add("let $f$ be $g$");
		inputStrList.add("the twisted $K$-theory $K^0(X,\\cA)$ is isomorphic to the Grothendieck group of Neumann equivalence class of projections in $C(X, \\K_\\cA)$");
		inputStrList.add("Integers $p$ and $q$ are coprime if and only if there exist integers $n$ and $m$ such that $np - mq = 1$");
		inputStrList.add("Suppose that $f :[a,b] \\[RightArrow] R $  is continuous and we define $F : [a,b] \\[RightArrow] R$ by  $F=\\int f dx$.");//
		inputStrList.add("Suppose that $f_n->g$ uniformly on $[a,b]$");
		inputStrList.add("Let $x \\elem R$. If $\\sum a_k$ converges conditionally, then there is some rearrangement of$\\sum a_k$  which converges to $x$");
		inputStrList.add("$F$ is an extension that is finite over $Q$");		
		inputStrList.add("Suppose that $f_n->f$ pointwise");
		inputStrList.add("There are field in the class $\\mathcal{X}$ that are not finite modifications of rings.");
		inputStrList.add("for all components $U$ of $supp(w)$ which are not annuli");		
		inputStrList.add("the holonomy of $\\partial \\Sigma$ has no fixed points");
		inputStrList.add("$\\lambda$ run over all pairs of partitions which are complementary with respect to $R$");
		inputStrList.add("$s_n$ does not converge");
		inputStrList.add("If the Alexander polynomial of $K$ is not $1$");		
		inputStrList.add("they do not intersect");
		inputStrList.add("The interchange of two distant critical points of the surface diagram does not change the induced map on homology");
		inputStrList.add("fix a rectangular Young diagram $R$ over a field");
		inputStrList.add("signed resolution $res(T)$ coincide with $RR$");

		//inputStrList.add("");
		//inputStrList.add("");
		//inputStrList.add("");
		//inputStrList.add("");
		//inputStrList.add("");
		
		/* Tests defluffing*/		
		
		//inputStrList.add("");
		//inputStrList.add("");
		//inputStrList.add("");
		//inputStrList.add("");
		//inputStrList.add("");
		//inputStrList.add("");
		//inputStrList.add("");
	}
	
}
