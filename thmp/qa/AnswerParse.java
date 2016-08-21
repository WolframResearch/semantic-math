package thmp.qa;

import java.util.HashMap;
import java.util.Map;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;

import thmp.search.ThmSearch;
import thmp.search.TriggerMathThm2;
import thmp.qa.QuestionAcquire.Formula;
import thmp.qa.QuestionAcquire.Formula.Variable;

/**
 * Parses the user's answers.
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

	static{
		//initializes in matrix for termDocMx.
		//get mx from 
		AnswerMx = QuestionAcquire.AnswerMx();
		String mx = ThmSearch.toNestedList(AnswerMx);
		
		try{			
			ml = MathLinkFactory.createKernelLink(ARGV);
			ml.discardAnswer();
			//set up the matrix corresponding to docMx, to be SVD'd. 
			ml.evaluate("mx=" + mx +"//N;");			
			ml.discardAnswer();	
			System.out.println(mx);
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
	 * Parses input, tries to fill in variables for a formula.
	 * Uses SVD and Nearest.
	 * @param input
	 * @param curState    , null means no prev state.
	 * @return Question to ask next, either ask for clarification, 
	 * confirmation, or ask about next variable.
	 */
	public static AnswerState parseInput(String input, AnswerState curState){
		
		
	}
	
	/**
	 * State of current QA session. Contains cur formula,
	 * next question to ask, keeps track of how many vars
	 * have been filled.
	 */
	public class AnswerState{
		private Formula curFormula;
		private String nextQuestion;
		//keep track of the values for each var, verified to have the right type, presented as a string
		
		//whether this var has been satisfied
		Map<Variable, Boolean> varSatMap = new HashMap<Variable, Boolean>();
		
		public AnswerState(Formula formula){
			
			//create and add all vars into varSatMap, default to false
			
		}
		
	}
	
	
}
