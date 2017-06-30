package thmp.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Multimap;

import thmp.parse.ParseState;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.parse.ParseState.VariableName;
import thmp.parse.Struct;
import thmp.parse.DetectHypothesis;
import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.parse.DetectHypothesis.Stats;
import thmp.search.SearchCombined.ThmHypPair;

/**
 * Test selection of hypotheses and definitions of tex variables 
 * that occur in documents.
 * 
 * @author yihed
 */
public class TestHypSelection {

	private static final String PREAMBLE = "\\documentclass{article}\n\\begin{document}";
	private static final String POSTAMBLE = "\\end{document}";
	
	/**
	 * 
	 * @return
	 */
	private static boolean test(String srcFilePath, Set<String> desiredLocalVars,
			List<String> desiredGlobalVars){
		
		BufferedReader srcFileReader = new BufferedReader(new FileReader(srcFilePath));		
		List<DefinitionListWithThm> definitionListWithThmList = new ArrayList<DefinitionListWithThm>();
		List<ThmHypPair> thmHypPairList = new ArrayList<ThmHypPair>();
		Stats stats = new Stats();
		ParseStateBuilder pBuilder = new ParseStateBuilder();
		ParseState parseState = pBuilder.build();
		DetectHypothesis.readAndParseThm(srcFileReader, parseState, definitionListWithThmList, thmHypPairList,
				stats, srcFilePath);
		Multimap<ParseState.VariableName, ParseState.VariableDefinition> localVariableNamesMMap = parseState.localVariableNamesMMap;
		Multimap<ParseState.VariableName, ParseState.VariableDefinition> globalVariableNamesMMap = parseState.getGlobalVariableNamesMMap();
		Set<String> localVarNamesSet = new HashSet<String>();
		Set<String> globalVarNamesSet = new HashSet<String>();
		
		for(VariableName varName : localVariableNamesMMap.keys()){
			localVarNamesSet.add(varName.nameStr());
		}
		
		
	}
	
	public void test0(){
		/*
		 * parseState.getGlobalVariableNamesMMap {alpha: NONE=[[ent{name=point}]]} localVariableNamesMMap:  {k: NONE=[[Type: symb, prev1:$K$]], D: NONE=[[ent{name=field}]]}
DetectHypothesis - thmHypPair $\alpha$ is closed. If $D\\supset K$ is a field. If $k(D)$ is algebraically closed in $K$, then $K$ is geometrically irreducible over $k$.  [test1.txt]
HYP: let $\alpha$ be a point in $X$ .
Let $\alpha$ be a point in $X$.
\begin{theorem}
$\alpha$ is closed. If $D\\supset K$ is a field. If $k(D)$ is algebraically closed in $K$, then $K$ is geometrically irreducible over $k$.
\end{theorem}
		 */
		//write content to file first		
		
		List<String> desiredGlobalVars = Arrays.asList(new String[]{"alpha"});
		
		List<String> desiredLocalVars = Arrays.asList(new String[]{"k"});
		
	}
	
	//let $\mathbb{z}$ be a point in $X$ .
	
	//Let $ \beta$ be a point in $X$. If $R = s$ is a ring. Given a group $G$.
}
