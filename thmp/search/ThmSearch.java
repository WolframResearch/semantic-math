package thmp.search;

import java.util.Arrays;
import java.util.Scanner;

import com.wolfram.jlink.*;

/**
 * Theorem search. Does the computation in WL using JLink. 
 * @author yihed
 *
 */
public class ThmSearch {

	/**
	 * Matrix of documents. Columns are documents.
	 * Rows are terms.
	 */
	private static int[][] docMx;
	public static final String[] ARGV = new String[]{"-linkmode", "launch", "-linkname", 
	"\"/Applications/Mathematica.app/Contents/MacOS/MathKernel\" -mathlink"};
	//number of nearest vectors to get for Nearest[]
	private static final int NUM_NEAREST = 3;
	
	private static KernelLink ml;
	
	static{
		//docMx = new int[][]{{0, 1, 0}, {1, 1, 0}, {0, 0, 1}, {1, 0, 0}};
		docMx = TriggerMathThm2.mathThmMx();

		//System.out.println(Arrays.deepToString(docMx));
		
		try{			
			ml = MathLinkFactory.createKernelLink(ARGV);
			ml.discardAnswer();
			//set up the matrix corresponding to docMx, to be SVD'd. 
			System.out.print(docMx.length + " " +docMx[0].length);
			String mx = toNestedList(docMx);
			System.out.println("Got to static initializer inside thmSearch");
			ml.evaluate("mx=" + mx +"//N;");				
			ml.discardAnswer();	
			
			System.out.println("Got the matrix");
			//ml.discardAnswer();
			// For now use the number of columns (theorem vectors).
			// # of words (for now) is a lot larger than the number of theorems.
			//int k = TriggerMathThm2.mathThmMx()[0].length;
			int k = 20;
			ml.evaluate("{u, d, v} = SingularValueDecomposition[mx, " + k +"];");
			//ml.waitForAnswer();
			ml.discardAnswer();
			System.out.println("Finished SVD");
			//Expr t = ml.getExpr();
			
			/*for(int i = 1; i <= docMx[0].length; i++){
			//should just be columns of V*, so rows of V
				ml.evaluate("p" + i + "= v[["+i+"]];");
				ml.discardAnswer();
			}*/
			
			//String queryStr = TriggerMathThm2.createQuery("root");
			//System.out.println(queryStr);
			//ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"];");
			//ml.discardAnswer();
			ml.evaluate("q//N");
			ml.waitForAnswer();
			Expr v = ml.getExpr();
			System.out.println("exprs realQ? " + v);
			
			ml.evaluate("(p1.First@Transpose[q])//N");
			ml.waitForAnswer();
			Expr w = ml.getExpr();
			System.out.println("~W " + w);
			
		}catch(MathLinkException e){
			System.out.println("error at launch!");
			e.printStackTrace();
		}
	}
	
	/*
	 * Convert docMx from array form to a String
	 * that's a nested List for WL.
	 * 
	 */
	public static String toNestedList(int[][] docMx){
		StringBuilder sb = new StringBuilder();
		
		//hString s = "";
		//s += "{";
		sb.append("{");
		
		int docSz = docMx.length;
		for(int i = 0; i < docSz; i++){
			//s += "{";
			sb.append("{");
			int iSz = docMx[i].length;
			for(int j = 0; j < iSz; j++){
				String t = j == iSz-1 ? docMx[i][j] + "" : docMx[i][j] + ", ";
				//s += t;
				sb.append(t);
			}
			String t = i == docSz-1 ? "}" : "}, ";
			//s += t;
			sb.append(t);
		}
		//s += "}";
		sb.append("}");
		return sb.toString();
	}
	
	
	public static void main(String[] args) throws ExprFormatException{		
		
		try{
			//ml.discardAnswer();
			
			//String result = ml.evaluateToOutputForm("Transpose@" + toNestedList(docMx), 0);
			String result = ml.evaluateToOutputForm("4+4", 0);
			System.out.println(result);
			//result = ml.evaluateToOutputForm("IdentityMatrix[2]", 0);
			//result = ml.evaluateToOutputForm("Plus@@{4,2}", 0);
			//ml.evaluate("Transpose[{{1, 2},{3,4}}]");
			//ml.evaluate("SingularValueDecomposition@" + toNestedList(docMx) +"//N");
			//ml.putFunction("Transpose", 1);
			//ml.put("{{1,2},{3,4}}");
			
			//ml.evaluate("Transpose@{{1,2},{3,4}}");
			ml.evaluate("d1 = 3; ");
			
			//Expr expr = ml.getExpr();
			ml.discardAnswer();			
			ml.evaluate("d1+2");
			ml.waitForAnswer();
			//ml.waitForAnswer();
			Expr expr = ml.getExpr();			
			System.out.println(expr);
			
			//System.out.println(Arrays.toString(expr.dimensions()));
			System.out.println(expr.integerQ());

			System.out.println("~~~");
			//reads input theorem, generates query string, process query
			readThmInput(ml);
			
		}catch(MathLinkException|IndexOutOfBoundsException e){
			System.out.println("error during eval!" + e.getMessage());
			e.printStackTrace();
			return;
		}finally{
			ml.close();
		}		
	}
	
	/**
	 * Constructs the String query to be evaluated.
	 * Submits it to kernel for evaluation.
	 * @return
	 * @throws MathLinkException 
	 * @throws ExprFormatException 
	 */
	private static void findNearestVecs(KernelLink ml, String queryStr) 
			throws MathLinkException, ExprFormatException{
		//System.out.println("queryStr: " + queryStr);
		//String s = "";
		//transform query vector to low dimensional space 
		//String query = "{{1,1,0,0}}";
		ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"];");
		ml.discardAnswer();
		
		//use Nearest to get numNearest number of nearest vectors, 
		int numNearest = NUM_NEAREST;
		//ml.evaluate("v[[1]]");
		//ml.getExpr();
		//System.out.println("DIMENSIONS " +ml.getExpr());
		
		//ml.evaluate("q");
		//ml.getExpr();
		//System.out.println("q " +ml.getExpr());
		
		ml.evaluate("Nearest[v->Range[Dimensions[v][[1]]], First[Transpose[q]],"+numNearest+"]");
		Expr nearestVec = ml.getExpr();
		//System.out.println(nearestVec.length() + "  " + Arrays.toString((int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)));
		for(int d : (int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)){
			System.out.println(TriggerMathThm2.getThm(d));	
			//System.out.println("thm vec: " + TriggerMathThm2.createQuery(TriggerMathThm2.getThm(d)));
		}
		
		
	}
	
	private static String readThmInput(KernelLink ml) throws MathLinkException, ExprFormatException{
		String query = "";
		Scanner sc = new Scanner(System.in);
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			query = TriggerMathThm2.createQuery(thm);
			//processes query
			findNearestVecs(ml, query);
		}		
		sc.close();
		return query;
	}
	
	//little function that tests various inputs
	private static void present(Expr expr) throws ExprFormatException{
		System.out.print(expr.length());
		System.out.println("matrixQ" + expr.matrixQ());
		System.out.println("asArray" + expr.asArray(Expr.REAL, 2));
		double[][] ar = (double[][])expr.asArray(Expr.INTEGER, 2);
		for(double[] i : ar){
			for(double j : i){
			System.out.print(j +  " ");
		}
			System.out.println();
		}
		
		System.out.println("Dimensions" + expr.dimensions()[0] +" " + expr.dimensions()[1]);
		System.out.println(expr.dimensions().length);
	}	
	
}
