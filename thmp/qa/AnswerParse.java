package thmp.qa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.google.common.collect.Multimap;
import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;

import thmp.search.TermDocMatrix;
import thmp.search.TriggerMathThm2;
import thmp.qa.AnswerParse.AnswerState;
import thmp.qa.QuestionAcquire.Formula;
import thmp.qa.QuestionAcquire.Formula.Variable;

/**
 * Parses the user's answers.
 * Called by QuestionSearch.
 * @author yihed
 *
 */
public class AnswerParse {
	/**
	 * Matrix of documents. Columns are documents.
	 * Rows are terms.
	 */
	private static int[][] AnswerMx;
	public static final String[] ARGV = new String[]{"-linkmode", "launch", "-linkname", 
	"\"/Applications/Mathematica.app/Contents/MacOS/MathKernel\" -mathlink"};
	
	private static KernelLink ml;
	public static Multimap<String, Formula> varFormulaMap = QuestionAcquire.varFormulaMMap();
	
	static{
		//initializes in matrix for termDocMx.
		AnswerMx = QuestionAcquire.AnswerMx();
		String mx = TermDocMatrix.toNestedList(AnswerMx);
		
		try{			
			ml = MathLinkFactory.createKernelLink(ARGV);
			ml.discardAnswer();
			//set up the matrix corresponding to docMx, to be SVD'd. 
			ml.evaluate("mx=" + mx +"//N;");			
			ml.discardAnswer();	
			//System.out.println(mx);
			
			//ml.evaluate("");
			
			//ml.discardAnswer();
			// For now use the number of columns (theorem vectors).
			// # of words (for now) is a lot larger than the number of theorems.
			//int k = TriggerMathThm2.mathThmMx()[0].length;
			/*int k = 3;
			ml.evaluate("{u, d, v} = SingularValueDecomposition[mx, " + k +"];");
			//ml.waitForAnswer();
			ml.discardAnswer();
			System.out.println("Finished SVD");
			//Expr t = ml.getExpr();
			for(int i = 1; i <= AnswerMx[0].length; i++){
			//should just be columns of V*, so rows of V
				ml.evaluate("p" + i + "= v[["+i+"]];");
				ml.discardAnswer();
			}*/
			
			/*String queryStr = TriggerMathThm2.createQuery("root");
			System.out.println(queryStr);
			ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"];");
			ml.discardAnswer();
			ml.evaluate("q//N");
			ml.waitForAnswer();
			Expr v = ml.getExpr();
			System.out.println("exprs realQ? " + v);
			
			ml.evaluate("(p1.First@Transpose[q])//N");
			ml.waitForAnswer();
			Expr w = ml.getExpr();
			System.out.println("~W " + w);*/
			
		}catch(MathLinkException e){
			System.out.println("Error at launch!");
			e.printStackTrace();
		}
	}
	/**
	 * 
	 * @returns the KernelLink
	 */
	public static KernelLink get_ml(){
		return ml;
	}
	
	/**
	 * Processes the initial input, figures out context, in particular what
	 * formula to use, corresponding to list of recommendations.
	 * Creates state from this.
	 * @param initialReply
	 * @param sc
	 * @throws MathLinkException 
	 * @throws ExprFormatException 
	 */
	public static AnswerState processInitial(String initialReply) throws MathLinkException, ExprFormatException{
		int[] nearestVecAr = get_nearestVecAr(initialReply);
		List<Formula> formulaList = new ArrayList<Formula>();
		
		for(int d : nearestVecAr){
			String curVar = QuestionAcquire.getVar(d);
			formulaList.addAll(varFormulaMap.get(curVar));
			//get variable giving the index in mx
			//System.out.println("variable " + curVar + "triggered formula " + varFormulaMap.get(curVar));			
		}
		
		Formula firstFormula = formulaList.get(0);
		//System.out.println("+++" + firstFormula + firstFormula.variableMap());
		AnswerState answerState = new AnswerState(formulaList);

		Variable var = answerState.getNextVar();
		String varQ = var.varQuestion();
		answerState.set_curVarName(var.name());
		
		//set the question, should also try to extract info from input
		answerState.set_nextQuestion("I see you want to learn about " + firstFormula.formulaName() + ". " + varQ);
		
		return answerState;
	}

	/**
	 * Retrieves array of nearest vectors, representing the column indices
	 * of the variables. 
	 * @param initialReply
	 * @return
	 * @throws MathLinkException
	 * @throws ExprFormatException
	 */
	private static int[] get_nearestVecAr(String initialReply) throws MathLinkException, ExprFormatException {
		//String representation of query vector, eg {{1,0,1,0}}
		String queryStr = QuestionAcquire.createQuery(initialReply);
		//System.out.println("queryStr " + queryStr);
		//find the 3 nearest vectors, by finding the Nearest 3 to the input vector		
		int numNearest = 3;
		ml.evaluate("Nearest[Transpose[mx]->Range[Dimensions[mx][[2]]], First@" + queryStr + "," + numNearest +"]");
		Expr nearestVec = ml.getExpr();
		//sSystem.out.println("nearestVec " + nearestVec);
		int[] nearestVecAr = (int[])nearestVec.part(1).asArray(Expr.INTEGER, 1);
		return nearestVecAr;
	}
	
	/**
	 * Parses input, tries to fill in variables for a formula.
	 * Uses SVD and Nearest.
	 * @param input	User's input
	 * @param curState  current state containing info on formula, etc, 
	 * null means no prev state.
	 * @return Question to ask next, either ask for clarification, 
	 * confirmation, or ask about next variable.
	 * @throws ExprFormatException 
	 * @throws MathLinkException 
	 * @throws ClassNotFoundException 
	 * @throws Exception 
	 */
	public static AnswerState parseInput(String input, AnswerState curState) throws IllegalArgumentException, MathLinkException, ExprFormatException, ClassNotFoundException{
		if(curState == null){
			throw new IllegalArgumentException("curState cannot be null");
		}
		
		if(input.toLowerCase().matches("no|stop")){
			System.out.println("Let's try again. What would you like to know?");
			return processInitial(input);
		}
		//this method should add variable (based on input) to the current formula in curState, 
		//and update the question in curState to prod the user more, or ask user for clarification
		//if the input does not fit as an answer.
		
		//update curState according to input. Adds input 
		int[] nearestVecAr = get_nearestVecAr(input);
		//need set of integers, containing indices of nearby variables.
		Set<Integer> nearestVarIndexSet = new HashSet<Integer>();
		Formula curFormula = curState.curFormula;
		String curVarName = curState.curVarName;
		String[] inputAr = input.split("\\s+");
		
		for(int d : nearestVecAr){
			//??
			String curVar = QuestionAcquire.getVar(d);
			nearestVarIndexSet.add(d);			
			//get variable giving the index in mx
			//System.out.println("variable " + curVar);			
		}
		String prevQuestion = curState.nextQuestion; 
		
		//if response does not have answer of right type or is not sufficiently close to 
		//the array corresponding to curVar, ask for more info
		if(inputAr.length > 2){
			//check to see if fit in right
			Integer curVarIndex = QuestionAcquire.variableIndexMap().get(curVarName);
			//index in variableMap starts from 0
			if(!nearestVarIndexSet.contains(curVarIndex+1)){
				if(!prevQuestion.substring(0, 6).equals("Your re")){
					curState.set_nextQuestion("I don't quite understand, " + curState.nextQuestion);
				}else{
					curState.set_nextQuestion("I don't quite understand, try again.");
				}
				return curState;
			}
			
		}
		
		//parse the input here?? --to be incorporated!
		//right now scans for input of the right type, etc int, double, 
		//only with one slot for a variable for now. 
		//Should 
		Variable curVar = curFormula.variableMap().get(curVarName);
		String curVarAnswerType = curVar.answerType();
		boolean s = false;
		for(String word : inputAr){
			//use better way to determine type!
			//if( instanceof Class.forName(curVarAnswerType)){
			//just check integer or float for now
			
			if(word.trim().matches("\\d+\\.*\\d*")){
				//if(word.matches("\\d+\\.*\\d*")){
				boolean formulaSat = curState.addVariable(curVar, word);
				if(formulaSat){
					System.out.println("Thanks! Calculating ... ");
					System.out.println(curState.presentSat());
					curState.set_startOver(true);
					curState.set_nextQuestion("What do you want to find out next?");
				}else{
					//set next question
					Variable nextVar = curState.getNextVar();
					curState.set_curVarName(nextVar.name());
					curState.set_nextQuestion(nextVar.varQuestion());
				}
				
				s = true;
				break;
				
			}
			
		}
		if(!s){
			if(!prevQuestion.substring(0, 6).equals("Your re")){
				curState.set_nextQuestion("I don't quite understand, " + curState.nextQuestion);
			}else{
				curState.set_nextQuestion("I don't quite understand, try again");
			}
		}
		
		return curState;
		
	}
	
	/**
	 * State of current QA session. Contains cur formula,
	 * next question to ask, keeps track of how many vars
	 * have been filled.
	 */
	public static class AnswerState{
		//list of formulas, order corresponds to contextual relevance.
		//useful for providing user with more options.
		private List<Formula> formulaList;
		private Formula curFormula;
		//variableMap for this formula
		Map<String, Variable> variableMap;
		//question to ask next
		private String nextQuestion;
		//variable currently under consideration, corresponds to answer
		//to previous nextQuestion
		private String curVarName;
		//whether previous command was satisfied, i.e. whether to move to a new one.
		private boolean startOver;
		
		//keep track of the values for each var, verified to have the right type, presented as a string
		Map<Variable, String> varValueMap = new HashMap<Variable, String>();
		//sets of required and optional variables. Formula satisfied when requiredVarSet is empty.
		Set<String> requiredVarSet = new HashSet<String>();		
		Set<String> optionalVarSet = new HashSet<String>();
		
		public AnswerState(List<Formula> formulaList){
		
			this.formulaList = formulaList;			
			this.curFormula = formulaList.get(0);
			
			//create and add all vars into requiredVarSet and optionalVarSet, default to false
			variableMap = curFormula.variableMap();
			//System.out.println("variableMap " + variableMap);
			for(Variable var: variableMap.values()){
				if(!var.optional()){
					requiredVarSet.add(var.name());
				}else{
					optionalVarSet.add(var.name());
				}
			}
			
		}
		
		/**
		 * changes the current formula, abandons previous one.
		 * @param formula
		 */
		public void set_curFormula(Formula curFormula){
			
			requiredVarSet.clear();
			optionalVarSet.clear();
			
			variableMap = curFormula.variableMap();
			
			for(Variable var: variableMap.values()){
				if(!var.optional()){
					requiredVarSet.add(var.name());
				}else{
					optionalVarSet.add(var.name());
				}
			}
		}
		
		/**
		 * If a variable has been filled.
		 * @return whether curFormula has been fulfilled.
		 */
		public boolean addVariable(Variable var, String varValue){
			//updates variableMap
			if(!var.optional()){
				requiredVarSet.remove(var.name());
			}else{
				optionalVarSet.remove(var.name());
			}
			varValueMap.put(var, varValue);
			
			return requiredVarSet.isEmpty();
		}
		
		public String presentSat(){
			return varValueMap.toString();
		}
		
		/**
		 * Get name of next var (randomly determined).
		 * @return
		 */
		public Variable getNextVar(){
			String nextVar = "";
			//System.out.println("__requiredVarSet" + requiredVarSet);
			for(String varName : requiredVarSet){
				nextVar = varName;
				break;
			}			
			return curFormula.variableMap().get(nextVar);
		}
		
		public void set_startOver(boolean startOver){
			this.startOver = startOver;
		}
		
		public boolean startOver(){
			return startOver;
		}
		
		/**
		 * Set next question
		 * @param question Question to be set
		 * @return
		 */
		public void set_nextQuestion(String question){
			this.nextQuestion = question;
		}
		
		public String nextQuestion(){
			return nextQuestion;
		}
		
		/**
		 * Set curVariable
		 * @param question Question to be set
		 * @return
		 */
		public void set_curVarName(String var){
			this.curVarName = var;
		}

		/**
		 * Get curVariable.
		 * @return current variable
		 */
		public String curVarName(){
			return this.curVarName;
		}
		
		/**
		 * Whether formula has been satisfied
		 * @return
		 */
		public boolean formulaSat(){
			return requiredVarSet.isEmpty();
		}

	}
	
	
}
