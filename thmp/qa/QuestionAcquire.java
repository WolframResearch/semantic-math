package thmp.qa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.search.TermDocMatrix;
import thmp.search.TermDocMatrix.TermDocMatrixBuilder;

/**
 * Uses TermDocMatrix.java to build mx of documents-terms, 
 * 
 * Contains structure for Formulas.
 * @author yihed
 *
 */

public class QuestionAcquire {

	//matrix with answers, columns are keywords for an answer/var inside a formula
	//ie how a user would phrase it.
	private static final int[][] AnswerMx;
	//dictionary of formula's name and corresponding formula.
	private static Map<String, Formula> formulaDict = new HashMap<String, Formula>();
	//map of variable names, and the formulas they belong to. Could be multiple Formulas.
	private static Multimap<String, Formula> varFormulaMMap = ArrayListMultimap.create();
	//termDocMatrix instance used to produce the above maps and lists.
	private static TermDocMatrix termDocMx;
	
	/**
	 * List of variables, in order they are inserted into mathObjMx (as columns)
	 */
	private static final List<String> variableList;
	
	static{
		TermDocMatrixBuilder mxBuilder = new TermDocMatrix.TermDocMatrixBuilder();
		
		//add keywords to corresponding property,
		//First entry is formula name.
		//Second entry in String[] identifies the keyword, eg APR, subsequent entries are different Spellings,
		//or keywords that should trigger the head keyword, eg "annual percentage rate". 
		//should weigh the keywords!
		//make enum of the format of answers needed fill in the var, eg String, int, etc
		addFormulaVar(new String[] { "FixedRateMortgage", "APR", "What's the APR?", "Double", "annual", "1", "percentage", "1", "rate", "1"}, mxBuilder); 
		addFormulaVar(new String[] { "FixedRateMortgage", "MA", "What's the principle?", "Double", "mortgage", "1", "amount", "1", "principle", ".5"}, mxBuilder); 
		addFormulaVar(new String[] { "FixedRateMortgage", "MP", "How long is the loan period?", "Integer", "mortgage", "1", "period", "1", "months", ".4" }, mxBuilder); 
		addFormulaVar(new String[] { "GrossDomesticProductExpenditures", "GDP", "What's the gross domestic product?", "Integer", "gross", "1", "domestic", "1", "product", ".4" }, mxBuilder); 
		addFormulaVar(new String[] { "GrossDomesticProductExpenditures", "GDP", "What's the gross domestic product?", "Integer", "gross", "1", "domestic", "1", "product", ".4" }, mxBuilder); 
		
		
		termDocMx = mxBuilder.build();		
		
		AnswerMx = termDocMx.termDocMx();
		variableList = termDocMx.docList();
	}
	
	private static void addFormulaVar(String[] keywords, TermDocMatrixBuilder mxBuilder){
		mxBuilder.addQAKeywordToMathObj(keywords, formulaDict, varFormulaMMap);
	}
	
	/**
	 * Map of variables and their corresponding formulas.
	 * @return
	 */
	public static Multimap<String, Formula> varFormulaMMap(){
		return varFormulaMMap;
	}
	
	public static int[][] AnswerMx(){
		return AnswerMx;
	}
	
	public static List<String> variableList(){
		return variableList;
	}
	
	/**
	 * Get variable given its index (column number) in AnswerMx.
	 * (which is index+1 in docList)
	 * @param index
	 * @return
	 */
	public static String getVar(int index){
		return variableList.get(index-1);
	}
	
	/**
	 * Create query row vector, 1's and 0's.
	 * @param input
	 * @return String representation of query vector, eg {{1,0,1,0}}
	 */
	public static String createQuery(String input){
		return TermDocMatrix.createQuery(input, termDocMx);
	}
	
	/**
	 * Map of variables and its index in variableList.
	 * Also column number in AnswerMx.
	 * Variable names may not be unique!! Probably need to make
	 * key Variable, with custom .equals.
	 * @return
	 */
	public static Map<String, Integer> variableIndexMap(){
		return termDocMx.docIndexMap();
	}
	
	public static void main(String[] args){
		//
		
	}
	
	/**
	 * Class with formula info. Map of which columns are needed
	 * for which formula to be satisfied.
	 * 
	 */
	public static class Formula{
		//map of variable name, maybe var entry, like optional or not, default val, etc
		private Map<String, Variable> variableMap;
		private String formulaName;
		
		public Formula(String formulaName){
			this.formulaName = formulaName;
			this.variableMap = new HashMap<String, Variable>();
			
		}
		
		public void addVariable(String variableName, String varQuestion, String answerType){
			variableMap.put(variableName, new Variable(variableName, varQuestion, answerType));
		}
		
		public Map<String, Variable> variableMap(){
			return variableMap;
		}
		
		public String formulaName(){
			return formulaName;
		}
		
		/**
		 * Class of Formula entries in the FormulaMMap.
		 * Var entry, like optional or not, default val, etc
		 */
		public static class Variable{
			
			//name of variable, 2nd String in String[] added to termDocMx.
			//eg "MA", "APR"
			private String varName;
			//should set to false!***
			private boolean optional;
			//default value as a String
			private String defaultVal;
			//question for this variable
			private String varQuestion;
			//type of expected answer, eg Integer, Double
			private String answerType;
			
			public Variable(String varName, String varQuestion, String answerType){
				this.varName = varName;
				this.varQuestion = varQuestion;
				this.answerType = answerType;
			}
			
			public String varQuestion(){
				return varQuestion;
			}
			
			public String answerType(){
				return answerType;
			}
			
			public boolean optional(){
				return optional;
			}
			
			public String name(){
				return varName;
			}
			
			@Override
			public String toString(){
				return varName;
			}
		}
		
		@Override
		public String toString(){
			return formulaName + " {" + variableMap.keySet() + "}";
		}
	}
}
