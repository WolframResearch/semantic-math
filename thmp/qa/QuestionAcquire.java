package thmp.qa;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.search.TermDocMatrix;
import thmp.search.TermDocMatrix.TermDocMatrixBuilder;

/**
 * Uses TriggerMathThm2.java to build mx, 
 * Takes mx, 
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
	
	static{
		TermDocMatrixBuilder mxBuilder = new TermDocMatrix.TermDocMatrixBuilder();
		
		//add keywords to corresponding property,
		//First entry is formula name.
		//Second entry in String[] identifies the keyword, eg APR, subsequent entries are different Spellings,
		//or keywords that should trigger the head keyword, eg "annual percentage rate". 
		//should weigh the keywords!
		//make enum of the format of answers needed fill in the var, eg String, int, etc
		addFormulaVar(new String[] { "FixedRateMortgage", "APR", "What's the APR?", "annual", "1", "percentage", "1", "rate", "1"}, mxBuilder); 
		addFormulaVar(new String[] { "FixedRateMortgage", "MA", "What's the principle?", "mortgage", "1", "amount", "1", "principle", ".5"}, mxBuilder); 
		addFormulaVar(new String[] { "FixedRateMortgage", "MP", "How long is the loan period?", "mortgage", "1", "period", "1", "months", ".4" }, mxBuilder); 
		
		TermDocMatrix termDocMx = mxBuilder.build();		
		
		AnswerMx = termDocMx.termDocMx();
	}
	
	private static void addFormulaVar(String[] keywords, TermDocMatrixBuilder mxBuilder){
		mxBuilder.addQAKeywordToMathObj(keywords, formulaDict, varFormulaMMap);
	}
	
	public static Multimap<String, Formula> varFormulaMMap(){
		return varFormulaMMap;
	}
	
	public static int[][] AnswerMx(){
		return AnswerMx;
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
		
		public void addVariable(String variableName, String varQuestion){
			variableMap.put(variableName, new Variable(variableName, varQuestion));
			
		}
		
		/**
		 * Class of Formula entries in the FormulaMMap.
		 * Var entry, like optional or not, default val, etc
		 */
		public static class Variable{
			
			//name of variable, 2nd String in String[] added to termDocMx.
			private String varName;
			private boolean optional;
			//default value as a String
			private String defaultVal;
			//question for this variable
			private String varQuestion;
			
			public Variable(String varName, String varQuestion){
				this.varName = varName;
				this.varQuestion = varQuestion;
			}
			
			public String varQuestion(){
				return varQuestion;
			}
			
			public boolean optional(){
				return optional;
			}
		}
		
	}
}
