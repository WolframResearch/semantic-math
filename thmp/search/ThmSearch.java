package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.servlet.ServletContext;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.wolfram.jlink.*;

import thmp.parse.TheoremContainer;
import thmp.utils.FileUtils;
import thmp.utils.MathLinkUtils.WLEvaluationMedium;
import thmp.utils.WordForms;

import static thmp.utils.MathLinkUtils.evaluateWLCommand;

/**
 * Theorem search. Does the computation in WL using JLink. 
 * @author yihed
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
	private static final int NUM_NEAREST = 50;
	//private static final int NUM_SINGULAR_VAL_TO_KEEP = 20;
	//cutoff for a correlated term to be considered
	private static final int COR_THRESHOLD = 3;
	private static final int LIST_INDEX_SHIFT = 1;
	private static final String combinedTDMatrixRangeListName = "combinedRangeList";
	private static final boolean USE_FULL_MX = false;	
	
	/**
	 * Class for finding nearest giving query. Normally run on servlet, and not locally.
	 */
	public static class ThmSearchQuery{
		
		private static final int QUERY_VEC_LENGTH;
		//private static final KernelLink ml;	
		private static final String V_MX;
		//string of rule of V_MX to its range vector
		//private static final String V_MX_RULE_NAME;
		private static final boolean DEBUG = false;
		private static final String CACHE_BAG_NAME = "cacheBag";
		private static final String TIME_BAG_NAME = "timeBag";
		//distance threshold for Nearest. To be converted programmatically using samples!!
		private static final double DISTANCE_THRESHOLD;
		//total number of mx files
		private static final int TOTAL_MX_COUNT = ThmHypPairGet.totalBundleNum();
		//cap of mx count in cache. Each mx is about 1.3 mb. Make cap small initially for testing
		private static final int CACHE_MX_COUNT_CAP = 20;
		
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
		//this ml should only be used at initialization. 
		WLEvaluationMedium ml;				
		int vector_vec_length = -1;
		
		//try{
			ServletContext servletContext = CollectThm.getServletContext();
			//String pathToMx = "src/thmp/data/termDocumentMatrixSVD.mx";
			/*Need to load both projection matrices, and the matrix of combined 
			  projected thm vectors */
			//WL  initialization is redundant if running on server, should have been initialized with server pool.			
			/*mx file also depends on the system!*/
			String pathToProjectionMx = getSystemProjectionMxFilePath();

			ml = FileUtils.acquireWLEvaluationMedium();
			String msg = "Kernel instance acquired in ThmSearchQuery...";
			logger.info(msg);
			
			/*path for the combined list of projected vectors*/
			//***String combinedProjectedMxFilePath = getSystemCombinedProjectedMxFilePath();
			String fullMxPath = "src/thmp/data/"+TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME+".mx";
			
			if(null != servletContext){			
				//***this should be redundant, if webM initialization was run properly
				pathToProjectionMx = servletContext.getRealPath(pathToProjectionMx);
				//combinedProjectedMxFilePath = servletContext.getRealPath(combinedProjectedMxFilePath);
				fullMxPath = servletContext.getRealPath(fullMxPath);
			}
			
			//V_MX should be superceeded by cache!
			if(!USE_FULL_MX){
				V_MX = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME;
			}else{
				V_MX = TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME;
			}
			//if kernel pool acquisition were working, this should be done in initialization code, right now 
			//initialization code doesn't seem to be running??
			//***evaluateWLCommand(ml, "<<"+combinedProjectedMxFilePath, false, true);*/
			evaluateWLCommand(ml, "<<"+pathToProjectionMx, false, true);
			evaluateWLCommand(ml, "AppendTo[$ContextPath, \""+ TermDocumentMatrix.PROJECTION_MX_CONTEXT_NAME +"\"]", false, true);	
			
			/*if(!USE_FULL_MX){
				evaluateWLCommand(ml, combinedTDMatrixRangeListName + "= Range[Length["+V_MX+"]]", false, true);
			}*/
			if(null == servletContext){
				if(USE_FULL_MX){
					evaluateWLCommand(ml, "<<"+fullMxPath);
				}				
				if(USE_FULL_MX){				
					//make rows be theorems
					evaluateWLCommand(ml, V_MX + "= Transpose["+ V_MX + "]");
					evaluateWLCommand(ml, combinedTDMatrixRangeListName + "= Range[Length["+V_MX+"]];"
							+ V_MX + "= Normal["+V_MX+"]", false, true);				
					System.out.println("FULL DIM MX LEN (num thms) " + evaluateWLCommand(ml, "Length["+combinedTDMatrixRangeListName+"]", true, true));
				}
			}			
			/*V_MX_RULE_NAME = V_MX +"->"+ combinedTDMatrixRangeListName;
			evaluateWLCommand(ml, V_MX_RULE_NAME + "=" + V_MX +"->"+ combinedTDMatrixRangeListName, false, true);*/
			
			//ml.evaluate("AppendTo[$ContextPath, \""+ TermDocumentMatrix.PROJECTION_MX_CONTEXT_NAME +"\"];");
			//ml.discardAnswer();
			
			/*String vMx;
			if(USE_FULL_MX){
				vMx = TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME;
				evaluateWLCommand(ml, vMx + "= Transpose["+ vMx + "]");
			}else{
				vMx = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME;
			}*/
			//ml.evaluate("Nearest["+vMx+"->Range[Dimensions["+vMx+"][[1]]]
			//should uncompress using this code here.
			
			//ml.evaluate("Length[corMx[[1]]]");
			Expr vecLengthExpr = evaluateWLCommand(ml, "Length[" + TermDocumentMatrix.PROJECTION_MX_CONTEXT_NAME +"corMx]", true, true);
			//ml.evaluate("Length[" + TermDocumentMatrix.PROJECTION_MX_CONTEXT_NAME +"corMx]");
			//ml.waitForAnswer();			
			try{
				vector_vec_length = vecLengthExpr.asInt();
				String msg1 = "ThmSearch - mx row dimension (num of words): " + vector_vec_length;
				System.out.println(msg1);
				logger.info(msg1);
			}catch(ExprFormatException e){
				String msg1 = "ExprFormatException when getting row dimension! " + e.getMessage();
				logger.error(msg1);
				throw new IllegalStateException(msg1);
			}finally{
				FileUtils.releaseWLEvaluationMedium(ml);				
			}
		/*}catch(MathLinkException e){
			msg = "MathLinkException when loading mx file!";
			logger.error(msg + e);
			throw new IllegalStateException(msg, e);
		}*/
		QUERY_VEC_LENGTH = vector_vec_length;

		//*****load mx cache manager script.
		String cacheManagerPath = getSystemCacheManagerPath();
		evaluateWLCommand(ml, "<<"+cacheManagerPath, false, true);
		//initializeCache[totalMxCount_Integer, mxCountCap_Integer, numNearest_Integer]
		evaluateWLCommand(ml, "{"+CACHE_BAG_NAME+","+ TIME_BAG_NAME
				+"} = initializeCache["+TOTAL_MX_COUNT+"," +CACHE_MX_COUNT_CAP +","+ TermDocumentMatrix.NUM_SINGULAR_VAL_TO_KEEP +"]", false, true);
		/*System.out.println("Initializer: STUFFBAG: " +evaluateWLCommand(ml, "bag=Internal`Bag[];Internal`StuffBag[bag,1]",true, true));
		System.out.println("Initializer: STUFFBAG: " +evaluateWLCommand(ml, "Internal`StuffBag[bag,2];bag",true, true));*/
		
		System.out.println("Initializer: $VersionNumber: " +evaluateWLCommand(ml, "$VersionNumber",true, true));
		evaluateWLCommand(ml, "AppendTo[$ContextPath,\"Internal`\"]", false, true);
		
		//needs to happen after computing QUERY_VEC_LENGTH
		DISTANCE_THRESHOLD = computeDistanceThreshold(ml);
	}
	
	public static int getQUERY_VEC_LENGTH(){
		return QUERY_VEC_LENGTH;
	}
	
	/**
	 * Computes the threshold based on distances between lists of strings 
	 * and select words in them chosen as query words.
	 * @return
	 */
	private static double computeDistanceThreshold(WLEvaluationMedium medium){
		//**June 19, debugging for now, skip this step, since slows down startup.
		if(true) return 0.1;//0.06;// 0.02360689779;
		//first String is thm, second is query
		String thm0 = "$\\lambda$ run over all pairs of partitions which are complementary with respect to $R$";
		String thm1 = "The interchange of two distant critical points of the surface diagram does not change the induced map on homology";
		String thm2 = "let $A$ be some simply connected space, then $B$ is trivial if $B$ is the fundamental group of $A$";
		String thm3 = "A morphism of C-algebras $f : A \\longrightarrow B$ with axiom is called a noncommutative Serre fibration";
	
		String[][] thmAndQueryStringAr = new String[][]{
			{thm0, "complementary partitions"},
			{thm1, "critical points of surface diagram"},
			{thm1, "induced map on homology"},
			{thm2, "simply connected space"},
			{thm2, "fundamental group"},
			{thm3, "morphism of algebras"},
			{thm3, "noncommutative Serre fibration"}
		};
		evaluateWLCommand(medium, "totalDistance=0." , false, true);
		for(String[] thmQueryPair : thmAndQueryStringAr){
			//get distancecs
			String thm = thmQueryPair[0];
			String thmVecStr = TriggerMathThm2.createQueryNoAnno(thm);
			//System.out.println("thmVecStr : " +thmVecStr);
			String query = thmQueryPair[1];
			String queryVecStr = TriggerMathThm2.createQueryNoAnno(query);
			/*applies projection mx. Need to Transpose, so rows represent thms. queryVecStrTranspose is column vec.
			 * So are q0 and q.*/
			evaluateWLCommand(medium, "queryVecStrTranspose= Transpose[" + queryVecStr + "]; "
					+ "q0 = queryVecStrTranspose + 0.08*corMx.queryVecStrTranspose; q = dInverse.uTranspose.q0", false, true);
			//System.out.println("qExpr " + qExpr);
			evaluateWLCommand(medium, "thmVecStrTranspose= Transpose[" + thmVecStr + "]; "
					+ "t0 = thmVecStrTranspose + 0.08*corMx.thmVecStrTranspose; t = dInverse.uTranspose.t0", false, true);
			//System.out.println("tExpr " + tExpr);
			evaluateWLCommand(medium, "totalDistance+=EuclideanDistance[t, q]", false, true);
			//System.out.println("totalDistance: " +);	
		}
		//Use 3/2 for now, experiment and make it a constant! now around 0.0236
		Expr distanceExpr = evaluateWLCommand(medium, "3/2*totalDistance/"+thmAndQueryStringAr.length, true, true);
		double threshold = 0.;
		try {
			threshold = distanceExpr.asDouble();
		} catch (ExprFormatException e) {
			String msg = "ExprFormatException when evaluating threshold distance for Nearest!";
			System.out.println(msg);
			logger.error(msg);
		}
		String msg = "The threshold distance for Nearest is computed to be " + threshold;
		System.out.println(msg);
		logger.info(msg);		
		//figure out if 0!!
		if(0.0 == threshold){
			logger.error("Threshold is 0!!!");
			threshold = 0.03;
		}
		return threshold;
	}
	
	/**
	 * Constructs the String query to be evaluated.
	 * Submits it to kernel for evaluation.
	 * @param queryVecStr should be a vec and *not* English words, i.e. {{v1, v2, ...}}.
	 * The embedding dimension of queryVec is the full dimension.
	 * @param num is number of results to show.
	 * @return List of indices of nearest thms. Indices in, eg MathObjList 
	 * (Indices in all such lists should coincide).
	 * @throws MathLinkException 
	 * @throws ExprFormatException 
	 */
	public static List<Integer> findNearestVecs(String queryVecStr, int ... num){
		WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();
		//try{
			//ml.evaluate("corMx");
			//System.out.println("thmsearch - corMx : " + ml.getExpr());
			String msg = "Transposing and applying corMx...";
			logger.info(msg);
			//process query first with corMx. Convert to column vec.
			boolean getResult = false;
			Expr qVec = evaluateWLCommand(medium, "queryVecStrTranspose= Transpose[" + queryVecStr + "]; "
					+ "q0 = queryVecStrTranspose + 0.08*corMx.queryVecStrTranspose", getResult, true);
			//ml.evaluate("queryVecStrTranspose= Transpose[" + queryVecStr + "]; "
				//	+ "q0 = queryVecStrTranspose + 0.08*corMx.queryVecStrTranspose;");
			if(getResult){				
				logger.info("ThmSearch - transposed queryVecStr: " + qVec);				
			}
			
			//ml.evaluate("corMx");
			//System.out.println("corMx:" +ml.getExpr());
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
			//this step is costly! Don't compute inverse each time!
			//ml.evaluate("q = dInverse.uTranspose.q;");
			//q no longer has 0.0 entries cause 0.1*corMx has been added. <--could still have 0.0 entries
			//ml.evaluate("q = dInverse.uTranspose.(q/.{0.0->mxMeanValue});");
			//ml.evaluate("q = Inverse[d].Transpose[u].(q/.{0.0->mxMeanValue});");
			
			//When queries are entered in quick succession,
			//QueryVecStr has wrong dimension?! Same dim as row/col dim of matrix d!!
			//ml.evaluate("q = Inverse[d].Transpose[u].(q/.{0.0->mxMeanValue})");
			//ml.evaluate("q = dInverse.uTranspose.(q0/.{0.0->mxMeanValue})");
			if(!USE_FULL_MX){
				/*q is column vector*/
				getResult = false;
				qVec = evaluateWLCommand(medium, "q = dInverse.uTranspose.q0", getResult, true);
				//ml.evaluate("q = dInverse.uTranspose.q0;");
				//ml.discardAnswer();
				if(getResult){
					logger.info("ThmSearch - dInverse.uTranspose.q0): " + qVec);
					//System.out.println("qVec: " + qVec);
				}				
				if(DEBUG){					
					System.out.println("The Nontrivial Values in query vec: " + evaluateWLCommand(medium, 
							"q1=Transpose[q][[1]]; pos=Position[q1, Except[0.]]; Map[Part[q1, #]&, pos]", true, true));
				}
			}else{
				evaluateWLCommand(medium,"q = q0", false, true);
				System.out.println("Nontrivial Pos: " + evaluateWLCommand(medium, 
						"q1=Transpose[q0][[1]]; pos=Position[q1, Except[0.]]", true, true));
				System.out.println("Values at Pos: " + evaluateWLCommand(medium, 
						"Map[Part[q1, #]&, pos]", true, true));
			}			
			//ml.waitForAnswer();
			//System.out.println("ThmSearch - q after inverse of transpose: " + ml.getExpr());
			/*ml.evaluate("q = q + vMeanValue;");
			ml.discardAnswer();*/
			//System.out.println("q + vMeanValue: " + ml.getExpr());
		/*}catch(MathLinkException e){
			throw new IllegalStateException(e);
		}*/
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
		List<Integer> nearestVecList = new ArrayList<Integer>();
		int[] nearestVecArray;
		try{
			msg = "Applying Nearest[]...";
			System.out.println(msg);
			logger.info(msg);
			
			//Keep trying nearest functions, until sufficiently many below a certain distance threshold is obtained.
			//Attach bundle iterator to web session. Must iterate over same range as intersection search!!!
			//Should pass here the range of thms searched for intersection, and just search over these range.
			Iterator<Integer> mxCacheIter = ThmHypPairGet.createMxBundleKeyIterator();
			//System.out.println("Dimensions@First@Transpose[q] " + evaluateWLCommand(ml, "Dimensions[First@Transpose[q]]", true, true));
			//String vMx = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME;
			//count of thms already found.
			//System.out.println("getMxPathFromSymbol[mxIndex_Integer]: " +evaluateWLCommand(medium, "getMxPathFromSymbol[0]",true, true));
			
			int thmsFoundCount = 0;
			while(thmsFoundCount <= numNearest && mxCacheIter.hasNext()){
				int nextMxKey = mxCacheIter.next();
				String nextMxName = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME + nextMxKey;//make this into method!
				//load the mx first, if not already in memory. This does not load if mx already loaded in cache.
				//loadMx[mxIndex_Integer, cacheBag_, timeBag_]
				evaluateWLCommand(medium, "loadMx["+nextMxKey + ","+ CACHE_BAG_NAME + "," + TIME_BAG_NAME +"]", false, true);
				
				/*System.out.println("Length[nextMxName]: " +evaluateWLCommand(medium, "Length["+nextMxName+"]",true, true));
				System.out.println("nextMxName[[1;;10]]: " +evaluateWLCommand(medium, nextMxName+"[[1;;10]]",true, true));
				System.out.println("CACHE_BAG_NAME: " +evaluateWLCommand(medium, CACHE_BAG_NAME,true, true));
				System.out.println("cacheExceedsCapacity[cacheBag]: " +evaluateWLCommand(medium, "cacheExceedsCapacity[cacheBag]",true, true));*/
				
				//Function signature: findNearest[combinedTDMx_, queryVec_, threshold_Real, numNearest_Integer]
				//get distance, and only those indices that fall below a distance threshold.
				//***factor out First@Transpose[q]!// also factor out numNearest/2!
				/**June 19 Expr nearestVec = evaluateWLCommand(medium, "findNearest[" + nextMxName + ",First@Transpose[q],"+ DISTANCE_THRESHOLD +","
						+numNearest/2 +"] - " 
						+ LIST_INDEX_SHIFT, true, true);*/
				Expr nearestVec = evaluateWLCommand(medium, "findNearestDist[" + nextMxName + ",First@Transpose[q],"+ DISTANCE_THRESHOLD +","
						+numNearest/2 +"]", true, true);
				//System.out.println("ThmSearch-DISTANCES: "+(double[])nearestVec.part(2).asArray(Expr.REAL, 1));
				System.out.println("ThmSearch-DISTANCES: "+nearestVec.part(2));

				//first sublist is indices, second distances
				nearestVec = nearestVec.part(1);
				//***Expr nearestVec = evaluateWLCommand(medium, "Nearest["+V_MX_RULE_NAME +", First@Transpose[q],"+numNearest+", Method->\"Scan\"] - " 
						//+ LIST_INDEX_SHIFT, true, false);
				//ml.evaluate("Nearest["+V_MX+"->"+ combinedTDMatrixRangeListName +", First@Transpose[q],"+numNearest+", Method->\"Scan\"] - " 
					//	+ LIST_INDEX_SHIFT);
				
				msg = "SVD returned nearestVec! "+ nearestVec;
				System.out.println(msg);
				logger.info(msg);
				//use this when using Nearest
				//int[] nearestVecArray = (int[])nearestVec.part(1).asArray(Expr.INTEGER, 1);
				 //<--this line generates exprFormatException if sucessive entries are quickly entered.
				//System.out.println("resulting Expr nearestVec: " + nearestVec);
				//logger.info("ThmSearch - nearestVec: " + nearestVec);
				nearestVecArray = (int[])nearestVec.asArray(Expr.INTEGER, 1);
				
				thmsFoundCount += nearestVecArray.length;
				Integer[] nearestVecArrayBoxed = ArrayUtils.toObject(nearestVecArray);
				nearestVecList.addAll(Arrays.asList(nearestVecArrayBoxed));				
				//process distances , keep indices of high ranking ones. if sufficiently close and many, break
				//need to implement caching on WL side!				
			}
		}catch(ExprFormatException e){
			logger.error("ExprFormatException when searching for nearestVec! " + e);
			throw new IllegalStateException(e);
			//return Collections.emptyList();
		}finally{
			FileUtils.releaseWLEvaluationMedium(medium);
		}		
		
		//for(int i = nearestVecList.size()-1; i > -1; i--){
		System.out.println("Thms with hyp: ");
		for(int i = 0; i < nearestVecList.size(); i++){
			int thmIndex = nearestVecList.get(i);
			System.out.println(ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex));
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
		//String msg = "ThmSearch - query String formed. ";
		//logger.info(msg);
		//System.out.println(msg);
		
		if(WordForms.getWhiteEmptySpacePattern().matcher(query).matches()){
			return Collections.emptyList();
		}
		System.out.println("ThmSearch.java: about to call findNearestVecs()");
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
		/*May introduce cyclic dependencies if have nontrivial static initializer in this class! Check e.g. TheoremGet.java */
		
		//private static final String PATH_TO_MX = "FileNameJoin[{Directory[], \"termDocumentMatrixSVD.mx\"}]";		
		private static final String PATH_TO_MX = getSystemProjectionMxFilePath();
		private static final String PROJECTION_MX_CONTEXT_NAME = "TermDocumentMatrix`";
		//protected static final String PROJECTED_MX_CONTEXT_NAME = "ProjectedTDMatrixContext`";
		protected static final String FULL_TERM_DOCUMENT_MX_CONTEXT_NAME = "FullTDMatrix`";
		//projected full term-document matrix, so "v^T" in SVD.
		public static final String PROJECTED_MX_NAME = "ProjectedTDMatrix";
		public static final String FULL_TERM_DOCUMENT_MX_NAME = "FullTDMatrix";
		protected static final String COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME = "CombinedTDMatrix";
		protected static final String CACHE_MANAGER_PACKAGE_NAME = "CacheManager";
		private static final String D_INVERSE_NAME = "dInverse";
		private static final String U_TRANSPOSE_NAME = "uTranspose";
		private static final String COR_MX_NAME = "corMx";
		public static final String PARSEDEXPRESSION_LIST_FILE_NAME_ROOT = "parsedExpressionList";
		//Do not attach .dat at end of this file name.
		public static final String CONTEXT_VEC_PAIR_LIST_FILE_NAME = "contextRelationVecPairList";
		/*Name deliberately does not include .dat*/
		public static final String COMBINED_PARSEDEXPRESSION_LIST_FILE_NAME_ROOT = "combinedParsedExpressionList";
		private static final int NUM_SINGULAR_VAL_TO_KEEP = 30;
		
		private static KernelLink ml;
		/**
		 * Serialize the high-dimensional term-document mx, 
		 * as formed from one run of DetectHypothesis.java.  
		 * @param HighDimTDMatrix presented as *Sparse*Array's.
		 * fullTermDocumentMxPath such as "0208_001/0208/FullTDMatrix.mx".
		 */
		public static void serializeHighDimensionalTDMx(ImmutableList<TheoremContainer> defThmList,
				String fullTermDocumentMxPath, Map<String, Integer> thmWordsFreqMap){			
			//String HighDimTDSparseMatrix, 
			/*ml = MathLinkFactory.createKernelLink(ARGV);
			System.out.println("MathLink created! "+ ml);
			//discard initial pakets the kernel sends over.
			ml.discardAnswer();*/
			WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();
			//ml = FileUtils.getKernelLinkInstance();
			
			//int rowDimension = docMx.length;
			int rowDimension = thmWordsFreqMap.size();
			//int mxColDim = docMx[0].length;
			int mxColDim = defThmList.size();
			
			//set up the matrix corresponding to docMx, to be SVD'd. 
			//adjust mx entries based on correlation first	
			//StringBuilder mxSB = new StringBuilder("m = Developer`ToPackedArray@");
			//mxSB.append(toNestedList(docMx)).append("//N;");
			/* *Need* to specify dimension! Since the Automatic dimension might be less than 
			 * or equal to the size of the keywordList, if some words are not hit by the current thm corpus. */
			StringBuilder mxSB = TriggerMathThm2.sparseArrayInputSB(defThmList, thmWordsFreqMap) 
					.insert(0, "m=SparseArray[1+").append(",{"+rowDimension).append(",").append(mxColDim+"}];");
			//System.out.println("ThmSearch. - mxSB " + mxSB);
			String msg = "ThmSearch.TermDocumentMatrix - mxSB.length(): " + mxSB.length();
			System.out.println(msg);
			logger.info(msg);
			
			//ml.evaluate(mxSB.toString()); //mxSB.append(";") here causes memory bloat?!			
			evaluateWLCommand(medium, FULL_TERM_DOCUMENT_MX_NAME + "=" + mxSB.toString(), false, true);
			//System.out.println("ThmSearch.TermDocumentMatrix.SparseArray formed: " + mxSB);
			
			msg = "Kernel has the matrix!";
			logger.info(msg);
			System.out.println(msg);
			//don't use special context for now, March 2017
			evaluateWLCommand(medium, "DumpSave[\"" + fullTermDocumentMxPath + "\"," + FULL_TERM_DOCUMENT_MX_NAME + "]", false, true);
			FileUtils.releaseWLEvaluationMedium(medium);
		}
		
		/**
		 * Projects the full TD mx down to lower dimension given previosly created SVD matrices.
		 * @param fullTermDocumentMxPath path to full high-dim term document mx. e.g. 
		 * "0208_001/0208/FullTDMatrix.mx" 
		 * @param projectionMxPath Path to mx file with projection matrix context, created from previous SVD.
		 * @param termDocumentMxPath Path to projected term document matrix (as row vectors), e.g. 
		 * "0208_001/0208/ProjectedTDMatrix.mx"
		 */
		public static void projectTermDocumentMatrix(String fullTermDocumentMxPath, String projectionMxPath, 
				String projectedTermDocumentMxPath) {	
			String msg = "ThmSearch.TermDocumentMatrix.projectTermDocumentMatrix - starting projection";
			System.out.println(msg);
			logger.info(msg);
			WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();
			evaluateWLCommand(medium, "<<"+projectionMxPath + "; AppendTo[$ContextPath, \"" + PROJECTION_MX_CONTEXT_NAME + "\"]", false, true);
			//full mx that was DumpSave'd from one tar file.
			//may not be using context!
			evaluateWLCommand(medium, "<<"+fullTermDocumentMxPath// + "; AppendTo[$ContextPath," + PROJECTED_MX_CONTEXT_NAME + "]"
					, false, true);	
			//evaluateWLCommand(PROJECTED_MX_CONTEXT_NAME , true, false);
			String fullTDMxName = FULL_TERM_DOCUMENT_MX_NAME;
			String dInverseName = D_INVERSE_NAME;
			String uTransposeName = U_TRANSPOSE_NAME;
			String corMxName = COR_MX_NAME;
			ProjectionMatrix.applyProjectionMatrix(fullTDMxName, dInverseName, uTransposeName, 
					corMxName, PROJECTED_MX_NAME);
			evaluateWLCommand(medium, "DumpSave[\"" + projectedTermDocumentMxPath + "\", "+ PROJECTED_MX_NAME+ "]", false, true);
			msg = "ThmSearch.TermDocumentMatrix.projectTermDocumentMatrix - Done projecting";
			System.out.println(msg);
			logger.info(msg);
			FileUtils.releaseWLEvaluationMedium(medium);
		}
		/**
		 * The method with argument createTermDocumentMatrixSVD(thmList)
		 * should be preferred over this argument-less one.
		 */
		public static void createTermDocumentMatrixSVD() {	
			//docMx = TriggerMathThm2.mathThmMx();			
			//mx to keep track of correlations between terms, mx.mx^T
			//List<List<Integer>> corMxList = new ArrayList<List<Integer>>();
			try{			
				/*ml = MathLinkFactory.createKernelLink(ARGV);
				System.out.println("MathLink created! "+ ml);
				//discard initial pakets the kernel sends over.
				ml.discardAnswer();*/
				ml = FileUtils.getKernelLinkInstance();
				String msg = "Kernel instance acquired in createTermDocumentMatrixSVD...";
				logger.info(msg);
				//int rowDimension = docMx.length;
				int rowDimension = TriggerMathThm2.mathThmMxRowDim();
				//int mxColDim = docMx[0].length;
				int mxColDim = ThmHypPairGet.totalThmsCount();
				System.out.println("ThmSearch-TermDocumentMatrix - number of keywords: " + rowDimension);
				System.out.println("ThmSearch-TermDocumentMatrix - number of theorems: " + mxColDim);
				//set up the matrix corresponding to docMx, to be SVD'd.
				//adjust mx entries based on correlation first	
				//StringBuilder mxSB = new StringBuilder("m = Developer`ToPackedArray@");
				//mxSB.append(toNestedList(docMx)).append("//N;");
				/* *Need* to specify dimension! Since the Automatic dimension might be less than 
				 * or equal to the size of the keywordList, if some words are not hit by the current thm corpus. */
				StringBuilder mxSB = TriggerMathThm2.sparseArrayInputSB()
						.insert(0, "m=SparseArray[1+").append(",{"+rowDimension).append(",").append(mxColDim+"}];");
				//System.out.println("ThmSearch. - mxSB " + mxSB);
				msg = "ThmSearch.TermDocumentMatrix - mxSB.length(): " + mxSB.length();
				System.out.println(msg);
				logger.info(msg);
				
				//System.out.println("nested mx " + Arrays.deepToString(docMx));
				
				ml.evaluate(mxSB.toString()); //mxSB.append(";") here causes memory bloat?!
				
				msg = "Kernel has the matrix!";
				logger.info(msg);
				System.out.println(msg);
				
				boolean getMx = false;
				if(getMx){
					ml.waitForAnswer();			
					Expr expr = ml.getExpr();
					System.out.println("m " + expr);
				}else{	
					ml.discardAnswer();	
				}
				ml.evaluate("Begin[\""+ PROJECTION_MX_CONTEXT_NAME +"\"];");
				ml.discardAnswer();
				
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
						+ "], {.6, Infinity}, {0, 0}]/.Indeterminate->0.0;");			
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					//get correlation matrix, to put together correlated terms
					//in similar indices. 
					System.out.println("corMx " + expr);					
				}else{
					ml.discardAnswer();
				}
				
				msg = "Corr. mx clipped! Ready to add corMx to m.";
				logger.info(msg);
				System.out.println(msg);				
				
				//the entries in corMx.m can range from 0 to ~6. Need to be same percentage!! 
				//Need to combine the two mx creation processes!
				ml.evaluate("mx = m + .08*corMx.m;");
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					System.out.println("m + .08*corMx.m " + expr);
				}else{
					ml.discardAnswer();
				}				
				System.out.println("ThmSearch - cor matrix added!");
				
				//System.out.println(nearestVec.length() + "  " + Arrays.toString((int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)));
				String dimMsg = "Dimensions of docMx: " + rowDimension + " " +mxColDim + ". Starting SVD...";
				System.out.print(dimMsg);
				logger.info(dimMsg);
				
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
				//int k = mxColDim < 35 ? mxColDim : (mxColDim < 400 ? 35 : (mxColDim < 1000 ? 40 : (mxColDim < 3000 ? 50 : 60)));
				int k = mxColDim < NUM_SINGULAR_VAL_TO_KEEP ? mxColDim : NUM_SINGULAR_VAL_TO_KEEP;
				ml.evaluate("ClearSystemCache[]; {u, d, v} = SingularValueDecomposition[mx, " + k +"];");
				//ml.waitForAnswer();
				ml.discardAnswer();
				/*ml.evaluate("m");
				ml.waitForAnswer();
				System.out.println("!!!!-----m: " + ml.getExpr() + " k: " + k + " mxColDim: " + mxColDim);*/
				
				//ml.evaluate("u = u; dd = d; v = v;");
				//ml.discardAnswer();
				System.out.println("Finished SVD");
				logger.info("Finished SVD!");
				//randomly select column vectors to approximate mean
				//adjust these!
				int numRandomVecs = mxColDim < 500 ? 60 : (mxColDim < 5000 ? 100 : 150);
				ml.evaluate("mxMeanValue = Mean[Flatten[mx[[All, #]]& /@ RandomInteger[{1,"+ mxColDim +"}," + numRandomVecs + "]]];"
						+ "dInverse=Inverse[d]; uTranspose=Transpose[u];"
						);				
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
				
				ml.evaluate("DumpSave[\"" + PATH_TO_MX + "\", {TermDocumentMatrix`mxMeanValue, "
						+ "TermDocumentMatrix`uTranspose, TermDocumentMatrix`dInverse, TermDocumentMatrix`corMx}];");
				ml.discardAnswer();
			}catch(MathLinkException e){
				System.out.println("error at launch!");
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
		
		/**
		 * @param defThmList This instead of list of strings, so not to take up additional memory
		 * storing just Strings.
		 */
		public static void createTermDocumentMatrixSVD(ImmutableList<TheoremContainer> defThmList) {			
			//docMx = TriggerMathThm2.mathThmMx();			
			//mx to keep track of correlations between terms, mx.mx^T
			//List<List<Integer>> corMxList = new ArrayList<List<Integer>>();
			try{			
				/*ml = MathLinkFactory.createKernelLink(ARGV);
				System.out.println("MathLink created! "+ ml);
				//discard initial pakets the kernel sends over.
				ml.discardAnswer();*/
				ml = FileUtils.getKernelLinkInstance();
				String msg = "Kernel instance acquired in createTermDocumentMatrixSVD...";
				logger.info(msg);
				//int rowDimension = docMx.length;
				int rowDimension = TriggerMathThm2.mathThmMxRowDim();
				//int mxColDim = docMx[0].length;
				int mxColDim = defThmList.size();
				System.out.println("ThmSearch-TermDocumentMatrix - number of keywords: " + rowDimension);
				System.out.println("ThmSearch-TermDocumentMatrix - number of theorems: " + mxColDim);
				//set up the matrix corresponding to docMx, to be SVD'd. 
				//adjust mx entries based on correlation first	
				//StringBuilder mxSB = new StringBuilder("m = Developer`ToPackedArray@");
				//mxSB.append(toNestedList(docMx)).append("//N;");
				/* *Need* to specify dimension! Since the Automatic dimension might be less than 
				 * or equal to the size of the keywordList, if some words are not hit by the current thm corpus. */
				StringBuilder mxSB = TriggerMathThm2.sparseArrayInputSB(defThmList)
						.insert(0, "m=SparseArray[1+").append(",{"+rowDimension).append(",").append(mxColDim+"}];");
				//System.out.println("ThmSearch. - mxSB " + mxSB);
				msg = "ThmSearch.TermDocumentMatrix - mxSB.length(): " + mxSB.length();
				System.out.println(msg);
				logger.info(msg);
				
				ml.evaluate(mxSB.toString()); //mxSB.append(";") here causes memory bloat?!
				
				msg = "Kernel has the matrix!";
				logger.info(msg);
				System.out.println(msg);
				
				boolean getMx = false;
				if(getMx){
					ml.waitForAnswer();			
					Expr expr = ml.getExpr();
					System.out.println("m " + expr);
				}else{	
					ml.discardAnswer();	
				}
				ml.evaluate("Begin[\""+ PROJECTION_MX_CONTEXT_NAME +"\"];");
				ml.discardAnswer();
				
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
				
				boolean printCorMx = false;
				if(printCorMx){
					ml.evaluate("Transpose[m]");
					ml.waitForAnswer();
					System.out.println("ThmSearch - [Transpose[m]]: " + ml.getExpr());
				}
				//System.out.println("Done clipping!");	
				boolean getCorMx = false;
				
				/*The entries in clipped correlation are between 0.3 and 1. Correlation between *words*,
				  not theorems. */
				//subtract IdentityMatrix to avoid self-compounding
				ml.evaluate("corMx = Clip[Correlation[Transpose[m]]-IdentityMatrix[" + rowDimension 
						+ "], {.6, Infinity}, {0, 0}]/.Indeterminate->0.0;");
				
				//ml.waitForAnswer();
				//System.out.println("clipped corr mx: " + ml.getExpr());
				
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					//get correlation matrix, to put together correlated terms
					//in similar indices. 
					System.out.println("corMx " + expr);					
				}else{
					ml.discardAnswer();
				}
				msg = "Corr. mx clipped! Ready to add corMx to m.";
				logger.info(msg);
				System.out.println(msg);
				
				//the entries in corMx.m can range from 0 to ~6 //This should be the same percentage as 
				//when forming query vecs!
				ml.evaluate("mx = m + .08*corMx.m;");
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					System.out.println("ThmSearch - m + .08*corMx.m " + expr);
				}else{
					ml.discardAnswer();
				}
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
				String dimMsg = "Dimensions of docMx: " + rowDimension + " " +mxColDim + ". Starting SVD...";
				System.out.print(dimMsg);
				logger.info(dimMsg);
				//System.out.println(mx);
				
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
				//int minDim = 40;
				//int k = mxColDim < minDim ? mxColDim : (mxColDim < 400 ? minDim : (mxColDim < 1000 ? 45 : (mxColDim < 3000 ? 50 : 60)));
				int k = mxColDim < NUM_SINGULAR_VAL_TO_KEEP ? mxColDim : NUM_SINGULAR_VAL_TO_KEEP;
				ml.evaluate("ClearSystemCache[]; {u, d, v} = SingularValueDecomposition[mx, " + k +"];");
				//ml.waitForAnswer();
				ml.discardAnswer();
				/*ml.evaluate("m");
				ml.waitForAnswer();
				System.out.println("!!!!-----m: " + ml.getExpr() + " k: " + k + " mxColDim: " + mxColDim);*/
				
				msg = "Finished SVD";
				System.out.println(msg);
				logger.info(msg);
				//randomly select column vectors to approximate mean
				//adjust these!
				int numRandomVecs = mxColDim < 500 ? 60 : (mxColDim < 5000 ? 100 : 150);
				ml.evaluate("mxMeanValue = Mean[Flatten[mx[[All, #]]& /@ RandomInteger[{1,"+ mxColDim +"}," + numRandomVecs + "]]];"
						+ "dInverse=Inverse[d]; uTranspose=Transpose[u];"
						);				
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
				
				ml.evaluate("DumpSave[\"" + PATH_TO_MX + "\", \"TermDocumentMatrix`\"]");
				ml.discardAnswer();
			}catch(MathLinkException e){
				System.out.println("error at launch!");
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
		
	}/* end of TermDocumentMatrix class */
	
	/**
	 * Convert docMx from array form to a String
	 * that's a nested List for WL.
	 * @deprecated
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
		
		//KernelLink ml = FileUtils.getKernelLinkInstance();
		try{			
			//String result = ml.evaluateToOutputForm("Transpose@" + toNestedList(docMx), 0);
			//String result = ml.evaluateToOutputForm("4+4", 0);
			//result = ml.evaluateToOutputForm("IdentityMatrix[2]", 0);
			//result = ml.evaluateToOutputForm("Plus@@{4,2}", 0);
			//ml.evaluate("Transpose[{{1, 2},{3,4}}]");
			//ml.evaluate("SingularValueDecomposition@" + toNestedList(docMx) +"//N");
			
			//ml.evaluate("Transpose@{{1,2},{3,4}}");
			/*ml.evaluate("d1 = 3;");
			ml.discardAnswer();			
			ml.evaluate("d1+2");
			ml.waitForAnswer();
			Expr expr = ml.getExpr();			
			System.out.println(expr);
			
			//System.out.println(Arrays.toString(expr.dimensions()));
			System.out.println(expr.integerQ());*/

			System.out.println("~~~");
			//reads input theorem, generates query string, process query
			ThmSearchQuery.readInputAndSearch();
			
		}catch(IndexOutOfBoundsException e){			
			logger.error("IndexOutOfBoundsException during evaluation using MathLink." + e.getStackTrace());
			//e.printStackTrace();
			throw new IllegalStateException("IndexOutOfBoundsException during evaluation!", e);
		}
		finally{
			//ml.close();
		}		
	}
	
	/**
	 * Retrieves path to mx file containing SVD matrix data, 
	 * Supports Linux and OS X. 
	 * @return
	 */
	private static String getSystemProjectionMxFilePath(){
		String pathToMx = "src/thmp/data/termDocumentMatrixSVD.mx";
		//mx file also depends on the system!		
		//but only 32-bit vs 64-bit, not OS. Should check bit instead.
		
		/*String OS_name = System.getProperty("os.name");
		if(OS_name.equals("Mac OS X")){
			pathToMx = "src/thmp/data/termDocumentMatrixSVDmac.mx";
		}*/
		return pathToMx;
	}
	
	/**
	 * Retrieves path to matrix combining projected vecs.
	 * @return
	 */
	private static String getSystemCacheManagerPath(){
		String pathToMx = "src/thmp/scripts/" + TermDocumentMatrix
				.CACHE_MANAGER_PACKAGE_NAME + ".m";
		return FileUtils.getServletPath(pathToMx);
	}
	
	/**
	 * Retrieves path to matrix combining projected vecs.
	 * @return
	 */
	private static String getSystemCombinedProjectedMxFilePath(){
		String pathToMx = "src/thmp/data/tdmx/" + TermDocumentMatrix
				.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME + ".mx";
		return pathToMx;
	}
	
	/**
	 * Retrieves base of the path to combined parsedExpressionList[i].
	 * Does not include the index. Note name should not include ".dat".
	 * @return
	 */
	protected static String getSystemCombinedParsedExpressionListFilePathBase(){
		String combinedParsedExpressionListPath = "src/thmp/data/pe/"
				+TermDocumentMatrix.COMBINED_PARSEDEXPRESSION_LIST_FILE_NAME_ROOT;
		return combinedParsedExpressionListPath;
	}
	
}
