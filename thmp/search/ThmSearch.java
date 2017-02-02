package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import javax.servlet.ServletContext;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.jlink.*;

import thmp.utils.FileUtils;
import thmp.utils.WordForms;

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
	private static double[][] docMx;
	private static final Logger logger = LogManager.getLogger(ThmSearch.class);
	
	//public static final String[] ARGV = new String[]{"-linkmode", "launch", "-linkname", 
	//"\"/Applications/Mathematica2.app/Contents/MacOS/MathKernel\" -mathlink"};	
	//path for on Linux VM 
	//private static final String[] ARGV;
	
	//number of nearest vectors to get for Nearest[]
	private static final int NUM_NEAREST = 3;
	//private static final int NUM_SINGULAR_VAL_TO_KEEP = 20;
	//cutoff for a correlated term to be considered
	private static final int COR_THRESHOLD = 3;
	private static final int LIST_INDEX_SHIFT = 1;
	
	/**
	 * Class for finding nearest giving query.
	 */
	public static class ThmSearchQuery{
	private static final KernelLink ml;	
	static{		
		//use OS system variable to tell whether on VM or local machine, and set InstallDirectory 
		//path accordingly.
		/*String OS_name = System.getProperty("os.name");
		if(OS_name.equals("Mac OS X")){
			ARGV = new String[]{"-linkmode", "launch", "-linkname", 
					"\"/Applications/Mathematica2.app/Contents/MacOS/MathKernel\" -mathlink"};
		}else{
			//path on Linux VM
			//ARGV = new String[]{"-linkmode", "launch", "-linkname", 
					//"\"/usr/local/Wolfram/Mathematica/11.0/Executables/MathKernel\" -mathlink"};
			ARGV = new String[]{"-linkmode", "launch", "-linkname", "math -mathlink"};
		}*/
		
		//createTermDocumentMatrixSVD();
		ml = FileUtils.getKernelLinkInstance();
		String msg = "Kernel instance acquired...";
		logger.info(msg);
		
		try{
			ServletContext servletContext = CollectThm.getServletContext();
			//String pathToMx = "src/thmp/data/termDocumentMatrixSVD.mx";
			//mx file also depends on the system!
			String pathToMx = getSystemMxFilePath();
			
			if(null != servletContext){				
				pathToMx = servletContext.getRealPath(pathToMx);
			}
			
			ml.evaluate("<<" + pathToMx+";");
			ml.discardAnswer();
			
			//ml.evaluate("TermDocumentMatrix`mxMeanValue ");
			//System.out.println("mxMeanValue: " + ml.getExpr());
			ml.evaluate("AppendTo[$ContextPath, \""+ TermDocumentMatrix.MX_CONTEXT_NAME +"\"];");
			ml.discardAnswer();
			
			//should uncompress using this code here.
			/*ml.evaluate("mxMeanValue =" + TermDocumentMatrix.MX_CONTEXT_NAME + "mxMeanValue;"
					+ "corMx =" + TermDocumentMatrix.MX_CONTEXT_NAME + "corMx;"
					+ "d =" + TermDocumentMatrix.MX_CONTEXT_NAME + "d;"
					+ "u =" + TermDocumentMatrix.MX_CONTEXT_NAME + "u;"
					+ "v =" + TermDocumentMatrix.MX_CONTEXT_NAME + "v;");
			ml.discardAnswer();*/
			ml.evaluate("Length[corMx[[1]]]");
			System.out.println("corMx row dimension (num of words): " + ml.getExpr());
		}catch(MathLinkException e){
			msg = "MathLinkException when loading mx file! ";
			logger.error(msg + e);
			throw new IllegalStateException(msg, e);
		}
		
	}
	
	/**
	 * Constructs the String query to be evaluated.
	 * Submits it to kernel for evaluation.
	 * @param queryVecStr should be in vec form!
	 * @return List of indices of nearest thms. Indices in, eg MathObjList 
	 * (Indices in all such lists should coincide).
	 * @throws MathLinkException 
	 * @throws ExprFormatException 
	 */
	public static List<Integer> findNearestVecs(String queryVecStr, int ... num){
		//System.out.println("queryStr: " + queryStr);
		//String s = "";
		//transform query vector to low dimensional space 
		//String query = "{{1,1,0,0}}";
		//ml.evaluate("q = " + queryStr + ".mx.Transpose[mx];");
		//ml.discardAnswer();
		
		try{
			String msg = "Transposing and applying corMx...";
			logger.info(msg);
			//process query first with corMx		
			ml.evaluate("q = Transpose[" + queryVecStr + "] + 0.1*corMx.Transpose["+ queryVecStr +"]//N");
			ml.discardAnswer();
			//ml.waitForAnswer();
			//Expr qVec = ml.getExpr();
			//System.out.println("ThmSearch - qVec: " + qVec);
			//System.out.println("QUERY " + ml.getExpr().part(1));
			msg = "Applied correlation matrix to querty vec, about to Inverse[d].Transpose[u].(q/.{0.0->mxMeanValue}) ";
			System.out.println(msg);
			logger.info(msg);
			
			//ml.evaluate("Length[q]");
			//Expr qVecDim = ml.getExpr();
			//System.out.println("ThmSearch - queryStr: " + queryVecStr);
			
			//ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"];");
			//ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"/.{0.0->mxMeanValue}];");
			//this step is costly!
			ml.evaluate("q = Inverse[d].Transpose[u].(q/.{0.0->mxMeanValue});");
			//ml.evaluate("q = Inverse[d].Transpose[u].Transpose[q];");
			ml.discardAnswer();
			//ml.waitForAnswer();
			//System.out.println("ThmSearch - q after inverse of transpose: " + ml.getExpr());
			/*ml.evaluate("q = q + vMeanValue;");
			ml.discardAnswer();*/
			//System.out.println("q + vMeanValue: " + ml.getExpr());
		}catch(MathLinkException e){
			throw new IllegalStateException(e);
		}
		//vMeanValue
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
		
		/*ml.evaluate("q");
		ml.waitForAnswer();
		System.out.println("q " +ml.getExpr()); */		
		
		int[] nearestVecArray;
		try{
			String msg = "Applying Nearest[]...";
			System.out.println(msg);
			logger.info(msg);
			ml.evaluate("Nearest[v->Range[Dimensions[v][[1]]], First@Transpose[q],"+numNearest+"] - " + LIST_INDEX_SHIFT);
			
			//take largest inner product
			//ml.evaluate("Keys[TakeLargest[AssociationThread[Range[Dimensions[v][[1]]] -> v.First[Transpose[q]]], "+numNearest+"]]");
			//ml.evaluate("Ordering[v.First[Transpose[q]], -"+numNearest+"]");
			ml.waitForAnswer();
			Expr nearestVec = ml.getExpr();
			//System.out.println(nearestVec.length() + "  " + Arrays.toString((int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)));
			//turn into list.
			msg = "SVD returned nearestVec! ";// + nearestVec;
			System.out.println(msg);
			logger.info(msg);
			//use this when using Nearest
			//int[] nearestVecArray = (int[])nearestVec.part(1).asArray(Expr.INTEGER, 1);
			nearestVecArray = (int[])nearestVec.asArray(Expr.INTEGER, 1);
		}catch(MathLinkException e){
			logger.error("MathLinkException! " + e.getStackTrace());
			throw new RuntimeException(e);
		}catch(ExprFormatException e){
			logger.error("ExprFormatException! " + e.getStackTrace());
			throw new RuntimeException(e);
		}
		Integer[] nearestVecArrayBoxed = ArrayUtils.toObject(nearestVecArray);
		List<Integer> nearestVecList = Arrays.asList(nearestVecArrayBoxed);
		
		//for(int i = nearestVecList.size()-1; i > -1; i--){
		for(int i = 0; i < nearestVecList.size(); i++){
			int thmIndex = nearestVecList.get(i);
			System.out.println(TriggerMathThm2.getThm(thmIndex));
			//System.out.println("thm vec: " + TriggerMathThm2.createQuery(TriggerMathThm2.getThm(d)));
		}
		System.out.println("~~~~~");
		//System.out.println("nearestVecList from within ThmSearch.java: " + nearestVecList);
		return nearestVecList;
	}
	
	private static String readInputAndSearch(){
		
		String query = "";
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			query = TriggerMathThm2.createQueryNoAnno(thm);
			if(WordForms.getWhiteEmptySpacePattern().matcher(query).matches()){
				System.out.println("I've got nothing for you yet. Try again.");
				continue;
			}
			//processes query				
			findNearestVecs(query);
		}	
		sc.close();	
		return query;
	}
	
	/**
	 * Reads thm one at a time.
	 * @param thm is a thm input String
	 * @param numVecs number of cloests vecs to take
	 * @return list of indices of nearest thms. 
	 */
	public static List<Integer> findNearestThmsInTermDocMx(String thm, int numVec){
		
		List<Integer> nearestVecList = null;		
		String query = TriggerMathThm2.createQueryNoAnno(thm);
		if(query.equals("")){
			return Collections.emptyList();
		}
		System.out.println("ThmSearch.java: about to findNearestVecs()!");
		//processes query
		nearestVecList = findNearestVecs(query, numVec);
		
		//System.out.print("Within ThmSearch, nearestVecList: " + nearestVecList);
		return nearestVecList;
	}
	
	//helper function that tests various inputs
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
	
	}//end of class
	public static class TermDocumentMatrix{
		
		//private static final String PATH_TO_MX = "FileNameJoin[{Directory[], \"termDocumentMatrixSVD.mx\"}]";		
		private static final String PATH_TO_MX = getSystemMxFilePath();
		private static final String MX_CONTEXT_NAME = "TermDocumentMatrix`";
		private static KernelLink ml;
		/**
		 * 
		 */
		public static void createTermDocumentMatrixSVD() {
			
			docMx = TriggerMathThm2.mathThmMx();			
			//mx to keep track of correlations between terms, mx.mx^T
			//List<List<Integer>> corMxList = new ArrayList<List<Integer>>();
			try{			
				/*ml = MathLinkFactory.createKernelLink(ARGV);
				System.out.println("MathLink created! "+ ml);
				//discard initial pakets the kernel sends over.
				ml.discardAnswer();*/
				ml = FileUtils.getKernelLinkInstance();
				String msg = "Kernel instance acquired...";
				logger.info(msg);
				
				ml.evaluate("Begin[\""+ MX_CONTEXT_NAME +"\"]");
				ml.discardAnswer();
				//set up the matrix corresponding to docMx, to be SVD'd. 
				//adjust mx entries based on correlation first	
				StringBuilder mxSB = new StringBuilder("m = Developer`ToPackedArray@");
				mxSB.append(toNestedList(docMx)).append("//N;");
				
				int rowDimension = docMx.length;
				int mxColDim = docMx[0].length;
				msg = "mxSB.length(): " + mxSB.length();
				System.out.println(msg);
				logger.info(msg);
				
				//System.out.println("nested mx " + Arrays.deepToString(docMx));
				boolean getMx = false;
				
				ml.evaluate(mxSB.toString());
				//ml.evaluate("m={{1,2}}");
				msg = "Kernel has the matrix!";
				logger.info(msg);
				if(getMx){
					ml.waitForAnswer();			
					Expr expr = ml.getExpr();
					System.out.println("m " + expr);
				}else{	
					ml.discardAnswer();	
				}
				//ml.evaluate("m");
				//ml.waitForAnswer();
				//System.out.println("FIRST m : " + ml.getExpr());
				//corMx should be computed using correlation mx
				//or add a fraction of M.M^T.M
				//this has the effect that if ith term and jth terms
				//are correlated, and (i,k) is non zero in M, then make (j,k)
				//nonzero (of smaller magnitude than (i,K) in M.
				//clip the matrix 			
				boolean getMean = false;
				if(getMean){
					ml.evaluate("matrix = m.Transpose[m].m;");
					ml.discardAnswer();
					ml.evaluate("Mean[matrix//N]");
					ml.waitForAnswer();			
					Expr expr = ml.getExpr();
					System.out.println("Mean " + expr);
				}
				
				//ml.evaluate("corMx = Clip[ m.Transpose[m], {4, Infinity}, {0, 0} ].m;");
				//ml.evaluate("correlatedMx = IntegerPart[m.Transpose[m].m];");
				//ml.discardAnswer();			
				
				//System.out.println("Done clipping!");	
				boolean getCorMx = false;
				
				//the entries in clipped correlation are between 0.3 and 1.
				//subtract IdentityMatrix to avoid self-compounding
				ml.evaluate("corMx = Clip[Correlation[Transpose[m]]-IdentityMatrix[" + rowDimension 
						+ "], {.6, Infinity}, {0, 0}]/.Indeterminate->0;");
				msg = "Corr. mx clipped!";
				logger.info(msg);
				
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					System.out.println("corMx " + expr);
					//get correlation matrix, to put together correlated terms
					//in similar indices. 
					
				}else{
					ml.discardAnswer();
				}
				//the entries in corMx.m can range from 0 to ~6
				ml.evaluate("mx = m + .15*corMx.m;");
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					System.out.println("m + .15*corMx.m " + expr);
				}else{
					ml.discardAnswer();
				}
				
				//take IntegerPart, faster processing later on
				//ml.evaluate("mx = m + IntegerPart[0.2*corMx];");
				//ml.discardAnswer();
				
				//Expr r = ml.getExpr();
				//add to m
				
				//System.out.println("is matrix? " + r.part(1).matrixQ() + r.part(1));
				//System.out.println(Arrays.toString((int[])r.part(1).part(1).asArray(Expr.INTEGER, 1)));
				
				/*int corMxLen1 = r.part(1).length();
				for(int i = 0; i < corMxLen1; i++){
					Integer[] thm_iListBoxed = ArrayUtils.toObject((int[])r.part(1).part(i+1).asArray(Expr.INTEGER, 1));
					List<Integer> thm_iList = Arrays.asList(thm_iListBoxed);
					corMxList.add(thm_iList);
				}*/
				//adjust entries of docMx based on corMxList
				//do this in WL, not loops here!
				//int[][] corrAdjustedDocMx = corrAdjustDocMx(docMx, corMxList);
				//mx = toNestedList(corrAdjustedDocMx);
				
				//write matrix to file, so no need to form it each time
				
				//System.out.println(nearestVec.length() + "  " + Arrays.toString((int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)));
				String dimMsg = "Dimensions of docMx: " + docMx.length + " " +mxColDim + ". Starting SVD...";
				System.out.print(dimMsg);
				logger.info(dimMsg);
				//System.out.println(mx);
				
				//ml.evaluate("mx=" + mx +"//N;");
				//ml.discardAnswer();	
				
				/*ml.evaluate("Mean[Mean[0.2*Transpose[ Covariance[Transpose[mx]].mx ]]]");
				//ml.evaluate("mx");
				ml.waitForAnswer();
				Expr mean = ml.getExpr();
				System.out.print("Mean of Mean " + mean);
				
				//ml.evaluate("mx = mx + Clip[0.2*Transpose[mx.Correlation[Transpose[mx]]], {2, Infinity}, {0, 0}];");
				ml.evaluate("mx = mx + Clip[0.2*Transpose[Covariance[Transpose[mx]].mx], {.1, Infinity}, {0, 0}];");
				ml.discardAnswer(); */
				
				//trim down to make mx sparse again, and also don't want small correlations
				//ml.evaluate("mx = Clip[mx, {.2, Infinity}, {0, 0}];");
				//ml.discardAnswer();		
				
				//System.out.println("Done clipping");
				
				//add a small multiple of mx.mx^T.mx, so to make term i more 
				//prominent when a correlated term is present.
				//ml.evaluate("mx=mx+0.2*mx.Transpose[mx].mx;");
				//this was to take correlations into account
				//ml.evaluate("mx=mx.Transpose[mx].mx;");
				//ml.discardAnswer();
				
				//number of singular values to keep. Determined (roughly) based on the number of
				//theorems (col dimension of mx)
				//int k = NUM_SINGULAR_VAL_TO_KEEP;
				int k = mxColDim < 35 ? mxColDim : (mxColDim < 400 ? 35 : (mxColDim < 1000 ? 40 : (mxColDim < 3000 ? 50 : 60)));
				ml.evaluate("{u, d, v} = SingularValueDecomposition[mx//N, " + k +"];");
				//ml.waitForAnswer();
				ml.discardAnswer();
				ml.evaluate("m");
				ml.waitForAnswer();
				System.out.println("!!!!-----m: " + ml.getExpr() + " k: " + k + " mxColDim: " + mxColDim);
				
				//ml.evaluate("u = u; dd = d; v = v;");
				//ml.discardAnswer();
				System.out.println("Finished SVD");
				logger.info("Finished SVD!");
				//randomly select column vectors to approximate mean
				//adjust these!
				int numRandomVecs = mxColDim < 500 ? 60 : (mxColDim < 5000 ? 100 : 150);
				ml.evaluate("mxMeanValue = Mean[Flatten[mx[[All, #]]& /@ RandomInteger[{1,"+ mxColDim +"}," + numRandomVecs + "]]];");
				//ml.evaluate("mxMeanValue = Mean[Flatten[v]];");
				ml.discardAnswer();
				//System.out.println("mxMeanValue " + ml.getExpr());
				/*ml.evaluate("mxMeanValue = Mean[Flatten[mx]];");
				ml.discardAnswer();	*/
				//System.out.println(" mean of flattened mx " + ml.getExpr().part(1));
				
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
				ml.evaluate("End[];");
				ml.discardAnswer();
				
				ml.evaluate("DumpSave[\"" + PATH_TO_MX + "\", \"TermDocumentMatrix`\"];");
				ml.discardAnswer();
			}catch(MathLinkException e){
				System.out.println("error at launch!");
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	}/* end of TermDocumentMatrix class */
	
	/**
	 * Adjusts docMx based on corMxList: increase docMx[j][k] if corMxList.get(i).get(j)
	 * is high.
	 * @param docMx
	 * @param corMxList
	 * @return
	 * @deprecated
	 */
	private static int[][] corrAdjustDocMx(int[][] docMx, List<List<Integer>> corMxList){
		int docMxDim1 = docMx.length;
		int docMxDim2 = docMx[0].length;
		//must create new mx, since modifying docMx in place will mess up updates 
		int[][] corrDocMx = new int[docMxDim1][docMxDim2];
		
		//System.out.println("b=" +toNestedList(docMx));
		
		for(int i = 0; i < docMxDim1; i++){
			for(int k = 0; k < docMxDim2; k++){
				if(docMx[i][k] != 0){
					corrDocMx[i][k] = docMx[i][k];
					for(int j = 0; j < docMxDim1; j++){
						if(corMxList.get(i).get(j) > COR_THRESHOLD){
							//for ~1100 thms, /2 is too much addition, can skew results, /3 seems ok.
							corrDocMx[j][k] += Math.max(docMx[i][k]/3.0, .5); 
						}
					}
				}
			}
		}
		//System.out.println("a=" +toNestedList(corrDocMx));

		return corrDocMx;
	}
	/**
	 * Convert docMx from array form to a String
	 * that's a nested List for WL.
	 */
	private static String toNestedList(double[][] docMx){
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
		
		KernelLink ml = FileUtils.getKernelLinkInstance();
		try{			
			//String result = ml.evaluateToOutputForm("Transpose@" + toNestedList(docMx), 0);
			//String result = ml.evaluateToOutputForm("4+4", 0);
			//result = ml.evaluateToOutputForm("IdentityMatrix[2]", 0);
			//result = ml.evaluateToOutputForm("Plus@@{4,2}", 0);
			//ml.evaluate("Transpose[{{1, 2},{3,4}}]");
			//ml.evaluate("SingularValueDecomposition@" + toNestedList(docMx) +"//N");
			
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
			ThmSearchQuery.readInputAndSearch();
			
		}catch(MathLinkException e){
			logger.error("MathLinkException during evaluation:" + e.getStackTrace());
			//e.printStackTrace();
			throw new RuntimeException("MathLinkException during evaluation!", e);
		}catch(IndexOutOfBoundsException e){			
			logger.error("IndexOutOfBoundsException during evaluation using MathLink." + e.getStackTrace());
			//e.printStackTrace();
			throw new IllegalStateException("IndexOutOfBoundsException during evaluation!", e);
		}
		finally{
			ml.close();
		}		
	}
	
	/**
	 * Retrieves path to mx file containing SVD matrix data, 
	 * Supports Linux and OS X. 
	 * @return
	 */
	private static String getSystemMxFilePath(){
		String pathToMx = "src/thmp/data/termDocumentMatrixSVD.mx";
		//mx file also depends on the system!		
		String OS_name = System.getProperty("os.name");
		if(OS_name.equals("Mac OS X")){
			pathToMx = "src/thmp/data/termDocumentMatrixSVDmac.mx";
		}
		return pathToMx;
	}
	
	//used for initializing this class
	//public static void initialize(){		
	//}
}
