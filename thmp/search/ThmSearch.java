package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.lang3.ArrayUtils;

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
	private static final int NUM_SINGULAR_VAL_TO_KEEP = 20;
	//cutoff for a correlated term to be considered
	private static final int COR_THRESHOLD = 3;
	//mx to keep track of correlations between terms, mx.mx^T
	private static final List<List<Integer>> corMxList;
	private static KernelLink ml;
	
	static{
		//docMx = new int[][]{{0, 1, 0}, {1, 1, 0}, {0, 0, 1}, {1, 0, 0}};
		docMx = TriggerMathThm2.mathThmMx();
		corMxList = new ArrayList<List<Integer>>();
		try{			
			ml = MathLinkFactory.createKernelLink(ARGV);
			//discard initial pakets the kernel sends over.
			ml.discardAnswer();
			//set up the matrix corresponding to docMx, to be SVD'd. 
			//adjust mx entries based on correlation first			
			String mx = toNestedList(docMx);
			ml.evaluate("m=IntegerPart[" + mx +"//N];");				
			ml.discardAnswer();	
			ml.evaluate("corMx = m.Transpose[m]");
			//symmetric matrix containing correlations
			
			Expr r = ml.getExpr();
			//System.out.println("is matrix? " + r.part(1).matrixQ() + r.part(1));
			//System.out.println(Arrays.toString((int[])r.part(1).part(1).asArray(Expr.INTEGER, 1)));
			
			int corMxLen1 = r.part(1).length();
			for(int i = 0; i < corMxLen1; i++){
				Integer[] thm_iListBoxed = ArrayUtils.toObject((int[])r.part(1).part(i+1).asArray(Expr.INTEGER, 1));
				List<Integer> thm_iList = Arrays.asList(thm_iListBoxed);
				corMxList.add(thm_iList);
			}
			//adjust entries of docMx based on corMxList
			int[][] corrAdjustedDocMx = corrAdjustDocMx(docMx, corMxList);
			mx = toNestedList(corrAdjustedDocMx);
			//System.out.println(nearestVec.length() + "  " + Arrays.toString((int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)));
			//
			System.out.print("Dimensions of docMx: " + docMx.length + " " +docMx[0].length);
			//System.out.println(mx);
			
			ml.evaluate("mx=" + mx +"//N;");
			ml.discardAnswer();	
			//add a small multiple of mx.mx^T.mx, so to make term i more 
			//prominent when a correlated term is present.
			//ml.evaluate("mx=mx+0.2*mx.Transpose[mx].mx;");
			//this was to take correlations into account
			//ml.evaluate("mx=mx.Transpose[mx].mx;");
			//ml.discardAnswer();
			
			int k = NUM_SINGULAR_VAL_TO_KEEP;
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
			/*ml.evaluate("q//N");
			ml.waitForAnswer();
			Expr v = ml.getExpr();
			System.out.println("exprs realQ? " + v); 
			
			ml.evaluate("(p1.First@Transpose[q])//N");
			ml.waitForAnswer();
			Expr w = ml.getExpr();
			System.out.println("~W " + w);*/
			
		}catch(MathLinkException | ExprFormatException e){
			System.out.println("error at launch!");
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Adjusts docMx based on corMxList: increase docMx[j][k] if corMxList.get(i).get(j)
	 * is high.
	 * @param docMx
	 * @param corMxList
	 * @return
	 */
	private static int[][] corrAdjustDocMx(int[][] docMx, List<List<Integer>> corMxList){
		int docMxDim1 = docMx.length;
		int docMxDim2 = docMx[0].length;
		//must create new mx, since we modifying docMx in place will mess up la updates 
		int[][] corrDocMx = new int[docMxDim1][docMxDim2];
		
		//System.out.println("b=" +toNestedList(docMx));

		for(int i = 0; i < docMxDim1; i++){
			for(int k = 0; k < docMxDim2; k++){
				if(docMx[i][k] != 0){
					corrDocMx[i][k] = docMx[i][k];
					for(int j = 0; j < docMxDim1; j++){
						if(corMxList.get(i).get(j) > COR_THRESHOLD){
						//if(corMxList.get(i).get(j) > 1){
							//for ~1100 thms, /2 is too much addition, can skew results, /3 seems ok.
							corrDocMx[j][k] += Math.max(Math.round(docMx[i][k]/3), .5); 
						}
					}
				}
			}
		}
		//System.out.println("a=" +toNestedList(corrDocMx));

		return corrDocMx;
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
	
	
	public static void main(String[] args) {		
		
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
			readThmInput();
			
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
	 * @return List of indices of nearest thms. Indices in, eg MathObjList 
	 * (Indices in all such lists should coincide).
	 * @throws MathLinkException 
	 * @throws ExprFormatException 
	 * 
	 */
	private static List<Integer> findNearestVecs(KernelLink ml, String queryStr, int ... num) 
			throws MathLinkException, ExprFormatException{
		//System.out.println("queryStr: " + queryStr);
		//String s = "";
		//transform query vector to low dimensional space 
		//String query = "{{1,1,0,0}}";
		//ml.evaluate("q = " + queryStr + ".mx.Transpose[mx];");
		//ml.discardAnswer();
		ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"];");
		//ml.evaluate("q = Inverse[d].Transpose[u].Transpose[q];");
		ml.discardAnswer();
		
		//use Nearest to get numNearest number of nearest vectors, 
		int numNearest;
		if(num.length == 0){
			numNearest = NUM_NEAREST;
		}else{
			numNearest = num[0];
		}
		//ml.evaluate("v[[1]]");
		//ml.getExpr();
		//System.out.println("DIMENSIONS " +ml.getExpr());
		
		//ml.evaluate("q");
		//ml.getExpr();
		//System.out.println("q " +ml.getExpr());
		
		ml.evaluate("Nearest[v->Range[Dimensions[v][[1]]], First[Transpose[q]],"+numNearest+"]");
		Expr nearestVec = ml.getExpr();
		//System.out.println(nearestVec.length() + "  " + Arrays.toString((int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)));
		//turn into list.
		System.out.println(nearestVec);
		int[] nearestVecArray = (int[])nearestVec.part(1).asArray(Expr.INTEGER, 1);
		Integer[] nearestVecArrayBoxed = ArrayUtils.toObject(nearestVecArray);
		List<Integer> nearestVecList = Arrays.asList(nearestVecArrayBoxed);
		
		for(int d : nearestVecList){
			System.out.println(TriggerMathThm2.getThm(d));
			//System.out.println("thm vec: " + TriggerMathThm2.createQuery(TriggerMathThm2.getThm(d)));
		}
		System.out.println("~~~~~");
		return nearestVecList;
	}
	
	public static String readThmInput(){
		
		String query = "";
		Scanner sc = new Scanner(System.in);
		try{
			while(sc.hasNextLine()){
				String thm = sc.nextLine();
				query = TriggerMathThm2.createQueryNoAnno(thm);
				if(query.equals("")){
					System.out.println("I've got nothing for you yet. Try again.");
					continue;
				}
				//processes query				
				findNearestVecs(ml, query);
			}	
		}catch(MathLinkException|ExprFormatException e){
			e.printStackTrace();
		}finally{
			sc.close();
		}
		return query;
	}
	/**
	 * Reads thm one at a time.
	 * @param thm is a thm input String
	 * @param numVecs number of cloests vecs to take
	 * @return list of indices of nearest thms. 
	 */
	public static List<Integer> readThmInput(String thm, int numVec){
		
		List<Integer> nearestVecList = null;
		try{			
			String query = TriggerMathThm2.createQueryNoAnno(thm);
			if(query.equals("")){
				return Collections.emptyList();
			}
			//processes query
			nearestVecList = findNearestVecs(ml, query, numVec);
				
		}catch(MathLinkException|ExprFormatException e){
			e.printStackTrace();
		}
		return nearestVecList;
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
