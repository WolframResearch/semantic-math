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
	static{
		//docMx = new int[][]{{0, 1, 0}, {1, 1, 0}, {0, 0, 1}, {1, 0, 0}};
		docMx = TriggerMathThm2.mathThmMx();
		System.out.println(Arrays.deepToString(docMx));
	}
	
	/*
	 * Convert docMx from array form to a String
	 * that's a nested List for WL.
	 * 
	 */
	public static String toNestedList(int[][] docMx){
		String s = "";
		s += "{";
		int docSz = docMx.length;
		for(int i = 0; i < docSz; i++){
			s += "{";
			int iSz = docMx[i].length;
			for(int j = 0; j < iSz; j++){
				String t = j == iSz-1 ? docMx[i][j] + "" : docMx[i][j] + ", ";
				s += t;
			}
			String t = i == docSz-1 ? "}" : "}, ";
			s += t;
		}
		s += "}";
		return s;
	}
	
	public static final String[] ARGV = new String[]{"-linkmode", "launch", "-linkname", 
			"\"/Applications/Mathematica.app/Contents/MacOS/MathKernel\" -mathlink"};
	
	public static void main(String[] args) throws ExprFormatException{
		KernelLink ml = null;
		
		try{
			ml = MathLinkFactory.createKernelLink(ARGV);
		}catch(MathLinkException e){
			System.out.println("error at launch!");
			return;
		}
		
		try{
			ml.discardAnswer();
			
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
			
			//System.out.println(Arrays.toString(expr.args()[0].dimensions()));
			//System.out.println(expr.args()[0].matrixQ());
			//System.out.println(expr.args()[0]);
			//double[][] matrix = (double[][])((expr.args()[0]).asArray(Expr.REAL, 2));
			
			//System.out.println(Arrays.deepToString(matrix));
			//int[][] d = (int[][])((expr.args()[0]).asArray(Expr.INTEGER, 2));
			//double[][] d = (double[][])((expr.args()[0]).asArray(Expr.REAL, 2));
			//System.out.println(d[0][0]);
			
			//present(expr);
			
			//ml.putFunction("SingularValueDecomposition", 1);
			//ml.putFunction("IdentityMatrix", 1);
			//ml.put("{{1,2},{3,4}}");
			//ml.put("{{1,2},{3,4}}");
			//ml.put("2");
			//ml.putFunction("IdentityMatrix", 1);
			//ml.put(2);
			//ml.waitForAnswer();
			//ml.endPacket();
			//byte[] r = ml.getData(10);
			//double[][] r = ml.getDoubleArray2();
			//byte[] r = ml.getData(4);

			//System.out.print(r.length);
			//System.out.print(r);

			//System.out.println(ml.getData(4));
			//double[][] result1 = ml.getDoubleArray2();
			//byte[][] result1 = ml.getByteArray2();
			//ml.getByteString(0);
			//System.out.println(result1.length);
			/*for(double[] d : result1){
				for(double i : d){
					System.out.print(i + ", ");
				}
				System.out.println();
			} */
			/*for(byte[] d : result1){
				for(byte i : d){
					System.out.print(i + ", ");
				}
				System.out.println();
			}*/
			//System.out.println(result1);
			//System.out.println(ml.getData(10));
			//System.out.println(ml.getDoubleArray2());
			//System.out.println(ml.getByteString(0));
		}catch(MathLinkException e){
			System.out.println("error during eval!" + e.getMessage());
			e.printStackTrace();
			return;
		}finally{
			ml.close();
		}		
	}
	
	/**
	 * Constructs the String query to be evaluated.
	 * @return
	 * @throws MathLinkException 
	 * @throws ExprFormatException 
	 */
	private static String constructQuery(KernelLink ml, String queryStr) 
			throws MathLinkException, ExprFormatException{
		System.out.println("queryStr: " + queryStr);
		String s = "";
		//query matrix
		String mx = toNestedList(docMx);
		ml.evaluate("mx=" + mx +";");
		ml.discardAnswer();	
		// For now use the number of columns (theorem vectors).
		// # of words (for now) is a lot larger than the number of theorems.
		int k = TriggerMathThm2.mathThmMx()[0].length;
		ml.evaluate("{u, d, v} = SingularValueDecomposition[mx, " + k +"];");
		ml.discardAnswer();
		//transform query vector to low dimensional space 
		//String query = "{{1,1,0,0}}";
		String query = queryStr;
		ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+query+"]");
		ml.discardAnswer();
		double max = 0;
		//index i of pi that yields maximal inner product
		int index = -1;
		//assign the document (column) vectors
		for(int i = 1; i <= docMx[0].length; i++){
			//System.out.println("Got here");
			//document vectors as row vectors
			ml.evaluate("d" + i + "= Transpose[mx][["+ i + "]]");
			
			//System.out.println("d_i: " +ml.getExpr());
			ml.discardAnswer();
			//transformed document vectors to column vectors in space spanned by
			//columns of V*.
			
			ml.evaluate("p" + i + "= Inverse[d].Transpose[u].Transpose[{d"+i+"}];");
			ml.discardAnswer();
			//take inner product of q in low dimenensional space with pi
			ml.evaluate("First@Transpose[p" + i +"].First@Transpose[q]//N");
			Expr expr = ml.getExpr();
			//ml.waitForAnswer();
			//check to ensure that the returned value is a real number
			Expr[] exprs = expr.args();
			if(exprs.length == 0 || !exprs[0].realQ()){
				System.out.println("Returned dot product has wrong type!");
				break;
			}
			double dotProd = expr.args()[0].asDouble();
			if(dotProd > max){
				max = dotProd;
				index = i;
			}
		}
		System.out.println("max DotProd: " + max);
		//System.out.println("index: " + index);
		System.out.println(TriggerMathThm2.getThm(index));
		return s;
	}
	
	private static String readThmInput(KernelLink ml) throws MathLinkException, ExprFormatException{
		String query = "";
		Scanner sc = new Scanner(System.in);
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			query = TriggerMathThm2.createQuery(thm);
			constructQuery(ml, query);
		}		
		sc.close();
		return query;
	}
	
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
