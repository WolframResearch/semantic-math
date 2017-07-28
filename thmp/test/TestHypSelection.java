package thmp.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Multimap;

import org.junit.Assert;

import thmp.parse.ParseState;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.parse.ParseState.VariableName;
import thmp.parse.DetectHypothesis;
import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.parse.DetectHypothesis.Stats;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.utils.FileUtils;

/**
 * Test selection of hypotheses and definitions of tex variables 
 * that occur in documents.
 * 
 * @author yihed
 */
public class TestHypSelection {

	private static final String PREAMBLE = "\\documentclass{article}\n\\begin{document}";
	private static final String POSTAMBLE = "\\end{document}";
	private static final String BEGIN_THM = "\\begin{theorem}";
	private static final String END_THM = "\\end{theorem}";
		
	/**
	 * 
	 * @return
	 * @throws IOException 
	 */
	private static boolean checkContext(File srcFile, List<String> desiredLocalVars,
			List<String> desiredGlobalVars) throws IOException{
		
		
		BufferedReader srcFileReader = new BufferedReader(new FileReader(srcFile));		
		List<DefinitionListWithThm> definitionListWithThmList = new ArrayList<DefinitionListWithThm>();
		List<ThmHypPair> thmHypPairList = new ArrayList<ThmHypPair>();
		Stats stats = new Stats();
		ParseStateBuilder pBuilder = new ParseStateBuilder();
		ParseState parseState = pBuilder.build();
		DetectHypothesis.readAndParseThm(srcFileReader, parseState, definitionListWithThmList, thmHypPairList,
				stats, srcFile.getAbsolutePath());
		Multimap<ParseState.VariableName, ParseState.VariableDefinition> localVariableNamesMMap = parseState.localVariableNamesMMap;
		Multimap<ParseState.VariableName, ParseState.VariableDefinition> globalVariableNamesMMap = parseState.getGlobalVariableNamesMMap();		
		srcFile.delete();
		
		Set<String> localVarNamesSet = new HashSet<String>();
		Set<String> globalVarNamesSet = new HashSet<String>();
		
		for(VariableName varName : localVariableNamesMMap.keys()){
			localVarNamesSet.add(varName.nameStr());
		}
		for(String var : desiredLocalVars){
			if(!localVarNamesSet.contains(var)){
				return false;
			}
		}
		
		for(VariableName varName : globalVariableNamesMMap.keys()){
			globalVarNamesSet.add(varName.nameStr());
		}
		for(String var : desiredGlobalVars){
			if(!globalVarNamesSet.contains(var)){
				return false;
			}
		}
		
		return true;
	}
	
	public void test0() throws IOException{
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
		String texDoc = constructDoc("let $\\alpha$ be a point in $X$", "$\\alpha$ is closed.");
		
		List<String> desiredGlobalVars = Arrays.asList(new String[]{"alpha"});		
		List<String> desiredLocalVars = Arrays.asList(new String[]{"k"});
		
		File srcFile = createAndWriteToFile(texDoc);
		
		Assert.assertTrue(checkContext(srcFile, desiredLocalVars, desiredGlobalVars));
	}
	
	//let $\mathbb{z}$ be a point in $X$ .
	
	//Let $ \beta$ be a point in $X$. If $R = s$ is a ring. Given a group $G$.
	public void test1() throws IOException{		
		//let $\mathbb{z}$ be a point in $X$ .		
		//Let $ \beta$ be a point in $X$. If $R = s$ is a ring. Given a group $G$.
		String texDoc = constructDoc("let $\\beta$ be a point in $X$", "let $\\beta$ be a point in $X$");
		
		List<String> desiredGlobalVars = Arrays.asList(new String[]{"beta"});		
		List<String> desiredLocalVars = Arrays.asList(new String[]{"k"});
		
		//FileUtils.writeToFile(texDoc, srcFilePath);
		File srcFile = createAndWriteToFile(texDoc);		
		Assert.assertTrue(checkContext(srcFile, desiredLocalVars, desiredGlobalVars));
	}
	
	public void test2() throws IOException{
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
		String texDoc = constructDoc("If $D\\supset K$ is a field.", 
				"If $k(D)$ is algebraically closed in $K$, then $K$ is geometrically irreducible over $k$");
		
		List<String> desiredGlobalVars = Arrays.asList(new String[]{"alpha"});		
		List<String> desiredLocalVars = Arrays.asList(new String[]{"k"});
		
		File srcFile = createAndWriteToFile(texDoc);
		
		Assert.assertTrue(checkContext(srcFile, desiredLocalVars, desiredGlobalVars));
	}
	
	/**
	 * Creates temporary file, write to file, and return it.
	 * Wri
	 * @param texDoc
	 * @return
	 * @throws IOException 
	 */
	private static File createAndWriteToFile(String texDoc) throws IOException{
		File file = File.createTempFile("", "");
		FileUtils.writeToFile(texDoc, file.getAbsolutePath());
		return file;
	}
	
	/**
	 * Constructs latex document out of hyp and thm.
	 * @param hyp
	 * @param thm
	 * @return
	 */
	private static String constructDoc(String hyp, String thm){
		return PREAMBLE + "\n" + hyp + "\n" + BEGIN_THM + "\n" + thm + "\n" +
				END_THM + "\n" + POSTAMBLE;
	}
	
}
