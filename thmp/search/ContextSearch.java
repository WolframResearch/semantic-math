package thmp.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.utils.WordForms;
import static thmp.utils.MathLinkUtils.evaluateWLCommand;

/**
 * Search results based on context vectors produced during parsing.
 * 
 * @author yihed
 *
 */
public class ContextSearch implements Searcher<Map<Integer, Integer>>{

	//bare thm list, without latex \label's or \index's, or refs, etc
	//private static final List<String> bareThmList = CollectThm.ThmList.get_bareThmList();
	//private static final KernelLink ml = FileUtils.getKernelLinkInstance();
	
	//private static final int LIST_INDEX_SHIFT = 1;
	private static final Pattern BRACKETS_PATTERN = WordForms.BRACKETS_PATTERN();
	private static final boolean DEBUG = false;
	private static final Logger logger = LogManager.getLogger(SearchIntersection.class);
	
	private SearcherState<Map<Integer, Integer>> searcherState;
	
	static{
		//get the deserialized vectors from CollectThm instead of from thm vec file!
		//need string form!
		//allThmsContextVecStrList = CollectThm.ThmList.allThmsContextVecList();		
	}

	@Override
	public void setSearcherState(SearcherState<Map<Integer, Integer>> searcherState_){
		searcherState = searcherState_;
	}
	
	@Override
	public SearcherState<Map<Integer, Integer>> getSearcherState(){
		return this.searcherState;
	}
	
	@Override
	public List<Integer> search(String query, List<Integer> nearestThmIndexList){		
		return contextSearchMap(query, nearestThmIndexList, this);		
	}
	
	/**
	 * @param query input query, in English 
	 * @param nearestThmIndexList List of thm indices, resulting from other 
	 * search algorithms such as SVD and/or intersection. Indices are 0-based.
	 * Gets list of vectors from GenerateContextVectors.java, 
	 * pick out the nearest structual vectors using Nearest[].
	 * @return Gives an ordered list of vectors based on context.
	 */
	public static List<Integer> contextSearchMap(String query, List<Integer> nearestThmIndexList, 
			Searcher<Map<Integer, Integer>> searcher){
		//short-circuit if query contains only 1 word		
		
		logger.info("Starting context search...");
		int nearestThmIndexListSz = nearestThmIndexList.size();
		//could be 0 if, for instance, the words searched are all unknown to the word maps. 
		if(0 == nearestThmIndexListSz){ 
			System.out.println("contextSearch parameter nearestThmIndexList is empty!");
			return nearestThmIndexList;		
		}
		
		//String queryContextVec;
		Map<Integer, Integer> queryContextVecMap;
		if(null == searcher){
			queryContextVecMap = thmp.parse.GenerateContextVector.createContextVector(query);
		}else{
			//optimization that stores vec in searcherState, 
			//to avoid repeatedly parsing the same query string.
			SearcherState<Map<Integer, Integer>> searcherState;
			
			if(null == (searcherState = searcher.getSearcherState())){
				queryContextVecMap = thmp.parse.GenerateContextVector.createContextVector(query);
				searcher.setSearcherState(new SearcherState<Map<Integer, Integer>>(queryContextVecMap));
			}else{
				queryContextVecMap = searcherState.getQueryVec();
				if(null == queryContextVecMap){
					//query string parsed already, but non nontrivial query vec produced.
					return nearestThmIndexList;
				}
			}
		}
		//if context vec was not generated in ThmP1.java because the input was unable to get parsed.
		if(null == queryContextVecMap //|| "null".contentEquals(queryContextVec) //<--really!?
				|| queryContextVecMap.isEmpty()){			
			logger.warn("No context vector was formed for query: " + query);
			return nearestThmIndexList;
		}
		System.out.println("ContextSearch-selected thm indices: " + nearestThmIndexList);
		//System.out.println("ContextSearch - queryContextVecMap "+ queryContextVecMap);
		
		Map<Integer, List<Integer>> thmVecsTMap = new TreeMap<Integer, List<Integer>>(new thmp.utils.DataUtility.ReverseIntComparator());
		List<Map<Integer, Integer>> nearestThmVecMapList = new ArrayList<Map<Integer, Integer>>();
		//extract context vec maps for each thm
		for(int i = 0; i < nearestThmIndexListSz; i++){
			int thmIndex = nearestThmIndexList.get(i);
			ContextRelationVecPair vecPair = TheoremGet.getContextRelationVecFromIndex(thmIndex);
			Map<Integer, Integer> curThmVecMap = vecPair.contextVecMap();
			
			nearestThmVecMapList.add(curThmVecMap);
			//look for entries that coincide with queryContextVecMap, the ordering in nearestThmVecMapList
			//should be same as the ordering returned by other search methods
			int numCoinciding = 0;
			for(Map.Entry<Integer, Integer> queryVecEntry : queryContextVecMap.entrySet()){
				int wordIndex = queryVecEntry.getKey();
				Integer curThmVecMapEntryVal = curThmVecMap.get(wordIndex);
				//System.out.println("query vec key/val: " + wordIndex + ",  " +queryVecEntry.getValue() 
					//	+ ". curThmVecMapEntryVal " + curThmVecMapEntryVal + " equals: " +(queryVecEntry.getValue().equals(curThmVecMapEntryVal)));
				if(queryVecEntry.getValue().equals(curThmVecMapEntryVal)){
					numCoinciding++;
				}				
			}
			//System.out.println("ContextSearch - curThmVecMap "+ curThmVecMap);
			System.out.println("ContextSearch - numCoinciding " + numCoinciding);
			List<Integer> curList = thmVecsTMap.get(numCoinciding);
			if(null != curList){
				curList.add(thmIndex);
			}else{
				curList = new ArrayList<Integer>();
				curList.add(thmIndex);
				thmVecsTMap.put(numCoinciding, curList);
			}
		}
		
		//coalesce map entries into one list
		List<Integer> nearestVecList = new ArrayList<Integer>();
		for(Map.Entry<Integer, List<Integer>> entry : thmVecsTMap.entrySet()){
			nearestVecList.addAll(entry.getValue());			
		}
		System.out.println("ContextSearch - nearestContextVecs: "+nearestVecList);
		
		for(int i = 0; i < nearestVecList.size(); i++){
			int thmIndex = nearestVecList.get(i);
			System.out.println(TriggerMathThm2.getThm(thmIndex));
		}
	
		//System.out.println("keywordDict: " + TriggerMathThm2.keywordDict());
		//logger.info("Context search done!");
		
		if(null != nearestVecList){
			return nearestVecList;
		}else{
			return nearestThmIndexList;
		}
	}
	
	/**
	 * @param query input query, in English 
	 * @param nearestThmIndexList List of thm indices, resulting from other 
	 * search algorithms such as SVD and/or intersection. Indices are 0-based.
	 * Gets list of vectors from GenerateContextVectors.java, 
	 * pick out the nearest structual vectors using Nearest[].
	 * @return Gives an ordered list of vectors based on context.
	 */
	/*public static List<Integer> contextSearch(String query, List<Integer> nearestThmIndexList, 
			Searcher<String> searcher){
		//short-circuit if query contains only 1 word		
		
		logger.info("Starting context search...");
		int nearestThmIndexListSz = nearestThmIndexList.size();
		//could be 0 if, for instance, the words searched are all unknown to the word maps. 
		if(0 == nearestThmIndexListSz){ 
			System.out.println("contextSearch parameter nearestThmIndexList is empty!");
			return nearestThmIndexList;		
		}
		
		String queryContextVec;	
		if(null == searcher){
			queryContextVec = thmp.GenerateContextVector.createContextVector(query);
		}else{
			//optimization to store vec in searcherState, 
			//to avoid repeatedly parsing the same query string.
			SearcherState<String> searcherState;
			
			if(null == (searcherState = searcher.getSearcherState())){
				queryContextVec = thmp.GenerateContextVector.createContextVector(query);
				searcher.setSearcherState(new SearcherState<String>(queryContextVec));
			}else{
				queryContextVec = searcherState.getQueryVec();
				if(null == queryContextVec){
					//query string parsed already, but non nontrivial query vec produced.
					return nearestThmIndexList;
				}
			}
		}
		//if context vec was not generated in ThmP1.java because the input was unable to get parsed.
		if(null == queryContextVec //|| "null".contentEquals(queryContextVec) //<--really!?
				|| queryContextVec.length() == 0){			
			logger.warn("No context vector was formed for query: " + query);
			return nearestThmIndexList;
		}
		
		//short-circuit if context vec not meaninful (insignificant entries created)
		
		//create range vector for Nearest. Indices are 0 based. Write own .toString()!
		Matcher bracketsMatcher = BRACKETS_PATTERN.matcher(nearestThmIndexList.toString());
		String rangeVec = bracketsMatcher.replaceAll("{$1}");
		System.out.println("selected thm indices: "+rangeVec);
		
		StringBuffer nearestThmsContextVecSB = new StringBuffer("{");
		
		List<Integer> nearestVecList = null;
		
		//start with index 0
		for(int i = 0; i < nearestThmIndexListSz; i++){
			int thmIndex = nearestThmIndexList.get(i);
			ContextRelationVecPair vecPair = TheoremGet.getContextRelationVecFromIndex(thmIndex);
			String contextVecStr = vecPair.contextVecStr();
			
			if(i < nearestThmIndexListSz-1){
				//except at the end.
				nearestThmsContextVecSB.append(contextVecStr + ",");
			}else{
				nearestThmsContextVecSB.append(contextVecStr + "}");
			}			
		}
		
		//get the nearest thms from the list of thm (indices) passed in
		//String nearestContextVecsStr = null;
		String thmVecDim = null;
		String queryVecDim = null;
		boolean printInfoMsg = true;
		try{
			//get the average of the nonzero elements			
			ml.evaluate("nearestThmList = "+ nearestThmsContextVecSB +
					//+ "; nearestThmListFlat = Flatten[nearestThmList];"
					//+ "thmNonZeroPositions = Position[nearestThmList, _?(# != 0 &)][[All,2]]"
						"; Length[nearestThmList[[1]]]"
					);
			
			//ml.discardAnswer();			
			if(printInfoMsg){	
				ml.waitForAnswer();
				thmVecDim = ml.getExpr().toString();
				System.out.println("nearestThmVecDim " + thmVecDim);
			}else{
				ml.discardAnswer();
			}
			
			ml.evaluate("query = " + queryContextVec);
			
			boolean printContextVecs = false;
			if(printContextVecs){	
				ml.waitForAnswer();
				System.out.println("query vector in ContextSearch.java: " + ml.getExpr());
			}else{
				ml.discardAnswer();
			}	
			
			if(printInfoMsg){	
				ml.evaluate("Length[query]");
				ml.waitForAnswer();
				queryVecDim = ml.getExpr().toString();
			}*/
			
			/*ml.evaluate("numberNonZeroEntrySize = Length[thmNonZeroPositions]");
			ml.waitForAnswer();
			Expr expr = ml.getExpr();
			if(!expr.integerQ() || expr.asInt() == 0){
				//should log if this happens!
				System.out.println("Something is wrong; or no non-zero entries in theorem vectors!");
				return nearestThmIndexList;
			}
			
			ml.evaluate("nonZeroContextVecMean = Total[nearestThmListFlat]/numberNonZeroEntrySize //N");
			//ml.discardAnswer();	
			ml.waitForAnswer();
			System.out.println("nonZeroContextVecMean " + ml.getExpr());

			//get nonzero entries of query context vector
			ml.evaluate("queryContextVecPositions = Position[query, _?(# != 0 &)][[All,1]]");
			//ml.discardAnswer();
			ml.waitForAnswer();			
			System.out.println("queryContextVecPositions " + ml.getExpr());
			
			//take complement of queryContextVecPositions in numberNonZeroEntries
			ml.evaluate("thmNonZeroEntryPositions = Complement[Union[thmNonZeroPositions], queryContextVecPositions]");
			//ml.discardAnswer();
			ml.waitForAnswer();			
			System.out.println("thmNonZeroEntryPositions " + ml.getExpr());*/
			//replace the query at union of indices, with averages of nonzero entries in nearestThmsContextVecSB.
			//ReplacePart[{1, 2, 4, 5}, Transpose[{{1, 3, 4}}] -> 3]
			//ml.evaluate("q = ReplacePart[query, Transpose[{thmNonZeroEntryPositions}]->nonZeroContextVecMean]");
			//ml.discardAnswer();
			
			//get average of the nearestThmsContextVecSB!
			/*ml.evaluate("contextVecMean = Mean[Flatten[" + nearestThmsContextVecSB + "//N]];");
			ml.discardAnswer();
			ml.evaluate("q=" + queryContextVec + "/.{0->contextVecMean}");*/
			
			//ml.discardAnswer();
			/*extract the few positions from thm vectors, so Nearest only needs to find it for vecs of small lengths!!*/
			/*Zero out parts in thm vecs returned from intersection, that are zero in query vec. */		
			/*April 24, 2017
			 * ml.evaluate("PositionsToReplace = Transpose[{Complement[Range[Length[query]], queryContextVecPositions]}]; "
					+ "nearestThmsPartReplaced = Map[ReplacePart[#, PositionsToReplace->0] &, nearestThmList]" );
			
			if(printContextVecs){	
				ml.waitForAnswer();
				System.out.println("nearestThmsPartReplaced" + ml.getExpr());
			}else{
				ml.discardAnswer();
			}*/
			//get nonzero entries of query context vector
			/*evaluateWLCommand("queryContextVecPositions = Position[query, _?(# != 0 &)][[All,1]]", false, true);
			
			Expr nearestContextVecs = evaluateWLCommand(
					"Keys[SortBy[AssociationThread["+rangeVec+", nearestThmList], -Count[query[[queryContextVecPositions]] - #[[queryContextVecPositions]], 0] &]]", 
					true, true);
			
			//use rangeVec as list of indices.
			//List indices are 0-based.
			//ml.evaluate("Nearest[nearestThmsPartReplaced->"+ rangeVec +", query," + nearestThmIndexListSz +"]");
		
			/*ml.evaluate("Nearest[" + nearestThmsContextVecSB + "->"+ rangeVec +", query," 
					+ nearestThmIndexListSz +"]"); */			
			//ml.waitForAnswer();
			//Expr nearestContextVecs = ml.getExpr();
			//nearestContextVecsStr = nearestContextVecs.toString();
			/*System.out.println("nearestContextVecs: "+nearestContextVecs);			
			
			//use this when using Nearest
			//int[] nearestVecArray = (int[])nearestVec.part(1).asArray(Expr.INTEGER, 1);
			int[] nearestVecArray = (int[])nearestContextVecs.asArray(Expr.INTEGER, 1);
			Integer[] nearestVecArrayBoxed = ArrayUtils.toObject(nearestVecArray);
			nearestVecList = Arrays.asList(nearestVecArrayBoxed);
			
			//for(int i = nearestVecList.size()-1; i > -1; i--){
			for(int i = 0; i < nearestVecList.size(); i++){
				int thmIndex = nearestVecList.get(i);
				System.out.println(TriggerMathThm2.getThm(thmIndex));
				//System.out.println("thm vec: " + TriggerMathThm2.createQuery(TriggerMathThm2.getThm(d)));
			}
			
		}catch(MathLinkException | ExprFormatException e){
			e.printStackTrace();
			throw new IllegalStateException("thmVecDim: " + thmVecDim + " queryVecDim: " + queryVecDim, e);
		}
		//System.out.println("keywordDict: " + TriggerMathThm2.keywordDict());
		logger.info("Context search done!");
		
		if(null != nearestVecList){
			return nearestVecList;
		}else{
			return nearestThmIndexList;
		}
	}*/
	
	//Run stand-alone
	public static void main(String[] args){
	
		Scanner sc = new Scanner(System.in);
		
		//get all thms for now, to test 
				/*int allThmsListSz = CollectThm.ThmList.allThmsWithHypList().size();
				List<Integer> nearestVecList = new ArrayList<Integer>();
				for(int i = 0; i < 5; i++){
					nearestVecList.add(i);
				}*/
				
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			if(thm.matches("\\s*")) continue;
			
			thm = thm.toLowerCase();
			int NUM_NEAREST = 6;
			
			List<Integer> nearestVecList = ThmSearch.ThmSearchQuery.findNearestThmsInTermDocMx(thm, NUM_NEAREST);			
			
			if(nearestVecList.isEmpty()){
				System.out.println("I've got nothing for you yet. Try again.");
				continue;
			}
			
			//find best intersection of these two lists. nearestVecList is 1-based, but intersectionVecList is 0-based! 
			//now both are 0-based.
			
			List<Integer> bestCommonVecs = contextSearchMap(thm, nearestVecList, null);
			
			/*for(int d : bestCommonVecs){
				System.out.println(TriggerMathThm2.getThm(d));
			}*/
		}
		
		sc.close();
		
	}
	
}
