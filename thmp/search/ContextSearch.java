package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.parse.InitParseWithResources;
import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.utils.FileUtils;

/**
 * Search results based on context vectors produced during parsing.
 * 
 * @author yihed
 *
 */
public class ContextSearch implements Searcher<Map<Integer, Integer>>{
	
	//private static final int LIST_INDEX_SHIFT = 1;
	//private static final Pattern BRACKETS_PATTERN = WordForms.BRACKETS_PATTERN();
	private static final boolean DEBUG = FileUtils.isOSX() ? InitParseWithResources.isDEBUG() : false;
	private static final Logger logger = LogManager.getLogger(ContextSearch.class);
	private static final int CONTEXT_MATCH_DEFAULT = 2;
	private static final int SEARCH_SPAN_THRESHOLD = 2;
	
	private QueryVecContainer<Map<Integer, Integer>> searcherState;
	
	/*static{
		//get the deserialized vectors from CollectThm instead of from thm vec file!
		//need string form!
		//allThmsContextVecStrList = CollectThm.ThmList.allThmsContextVecList();		
	}*/

	@Override
	public void setSearcherState(QueryVecContainer<Map<Integer, Integer>> searcherState_){
		searcherState = searcherState_;
	}
	
	@Override
	public QueryVecContainer<Map<Integer, Integer>> getSearcherState(){
		return this.searcherState;
	}
	
	/**
	 * @param query In English.
	 */
	@Override
	public List<Integer> search(String query, List<Integer> nearestThmIndexList, SearchState searchState){		
		return contextSearchMap(query, nearestThmIndexList, this, searchState);		
	}
	
	/**
	 * Does context search.
	 * @param query input query, in English 
	 * @param nearestThmIndexList List of thm indices, resulting from other 
	 * search algorithms such as SVD and/or intersection. Indices are 0-based. 
	 * *Note* this modifies nearestThmIndexList in place.
	 * Gets list of vectors from GenerateContextVectors.java, 
	 * pick out the nearest structual vectors using Nearest[].
	 * @param @Nullable searcher Searcher instance containining stateful information,
	 * e.g. ParseState.
	 * @return Gives an ordered list of vectors based on context.
	 */
	public static List<Integer> contextSearchMap(String query, List<Integer> nearestThmIndexList, 
			Searcher<Map<Integer, Integer>> searcher, SearchState searchState){
		
		logger.info("Starting context search...  ");
		int nearestThmIndexListSz = nearestThmIndexList.size();
		//could be 0 if, for instance, the words searched are all unknown to the word maps. 
		if(0 == nearestThmIndexListSz){ 
			//System.out.println("contextSearch parameter nearestThmIndexList is empty!");
			return nearestThmIndexList;		
		}	
		
		/** the span is already addressed before triggering contextsearch. 
		 * Map<Integer, Integer> thmSpanMap = searchState.thmSpanMap();
		Integer firstThmSpan = thmSpanMap.get(nearestThmIndexList.get(0));
		if(null != searcher && null != firstThmSpan && firstThmSpan < SEARCH_SPAN_THRESHOLD ) {
			return nearestThmIndexList;
		}*/
		
		Map<Integer, Integer> thmScoreMap = searchState.thmScoreMap();
		
		Map<Integer, Integer> queryContextVecMap;
		if(null == searcher){
			queryContextVecMap = thmp.parse.GenerateContextVector.createContextVector(query, searchState);
		}else{
			//optimization that stores vec in searcherState, 
			//to avoid repeatedly parsing the same query string.
			QueryVecContainer<Map<Integer, Integer>> searcherState;
			
			if(null == (searcherState = searcher.getSearcherState())){
				queryContextVecMap = thmp.parse.GenerateContextVector.createContextVector(query, searchState);
				searcher.setSearcherState(new QueryVecContainer<Map<Integer, Integer>>(queryContextVecMap));
			}else{
				queryContextVecMap = searcherState.getQueryVec();
				if(null == queryContextVecMap){
					//query string parsed already, but non nontrivial query vec produced.
					return nearestThmIndexList;
				}
			}
		}
		//if context vec was not generated in ThmP1.java because the input was unable to get parsed.
		if(null == queryContextVecMap || queryContextVecMap.isEmpty()){			
			logger.warn("No context vector was formed for query: " + query);
			return nearestThmIndexList;
		}
		//System.out.println("***********ContextSearch -QUERY  queryContextVecMap*** " +queryContextVecMap);
		if(DEBUG) {
			//System.out.println("ContextSearch-selected thm indices: " + nearestThmIndexList);
			System.out.println("ContextSearch - queryContextVecMap " +queryContextVecMap);
		}
		Map<Integer, Integer> contextVecScoreMap = new HashMap<Integer, Integer>();

		computeContextVecScoreMap(nearestThmIndexList,
				nearestThmIndexListSz, queryContextVecMap, contextVecScoreMap);
		
		/*TreeSet<Integer> thmVecsTMap = new TreeSet<Integer>(
						new thmp.utils.DataUtility.IntMapComparator(contextVecScoreMap, thmScoreMap, 0.95));*/
		
		nearestThmIndexList.sort(new thmp.utils.DataUtility.IntMapComparator(contextVecScoreMap, thmScoreMap, 0.95));
		//thmVecsTMap.addAll(nearestThmIndexList);
		
		searchState.setContextVecScoreMap(contextVecScoreMap);
		//searchState.setContextScoreIndexTMap(thmVecsTMap);
		//coalesce map entries into one list. Iterator respects order.
		//List<Integer> nearestVecList = new ArrayList<Integer>();
		/*for(Map.Entry<Integer, List<Integer>> entry : thmVecsTMap.entrySet()){
			nearestVecList.addAll(entry.getValue());			
		}*/
		
		/*if(false && DEBUG) {
			System.out.println("ContextSearch - nearestContextVecs: " 
					+ " Thms, including hyp: ");
			for(int i = 0; i < nearestVecList.size(); i++){
				int thmIndex = nearestVecList.get(i);
				System.out.println(thmIndex + " " + ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex));
			}
		}*/
		/*if(!nearestVecList.isEmpty()){
			return nearestVecList;
		}else{
			return nearestThmIndexList;
		}*/
		return nearestThmIndexList;
	}

	/**
	 * Computes context vec scores for list of theorems, given the query context vec map.
	 * @param nearestThmIndexList
	 * @param nearestThmIndexListSz
	 * @param queryContextVecMap
	 * @param contextVecScoreMap
	 * @param thmScoreMap map of word-scores from intersection search.
	 * @return
	 */
	public static void computeContextVecScoreMap(List<Integer> nearestThmIndexList,
			int nearestThmIndexListSz, Map<Integer, Integer> queryContextVecMap,
			Map<Integer, Integer> contextVecScoreMap) {
		
		//TreeMap<Integer, List<Integer>> thmVecsTMap = new TreeMap<Integer, List<Integer>>(
		//		new thmp.utils.DataUtility.ReverseIntComparator());
		
		//extract context vec maps for each thm
		for(int i = 0; i < nearestThmIndexListSz; i++){
			int thmIndex = nearestThmIndexList.get(i);
			ContextRelationVecPair vecPair = TheoremGet.getContextRelationVecFromIndex(thmIndex);
			Map<Integer, Integer> curThmVecMap = vecPair.contextVecMap();
			if(DEBUG) System.out.println("ContextSearch - index/curThmVecMap " +thmIndex + " " +curThmVecMap);
			//nearestThmVecMapList.add(curThmVecMap);
			//look for entries that coincide with queryContextVecMap, the ordering in nearestThmVecMapList
			//should be same as the ordering returned by other search methods
			int numCoinciding = 0;
			for(Map.Entry<Integer, Integer> queryVecEntry : queryContextVecMap.entrySet()){
				int wordIndex = queryVecEntry.getKey();				
				Integer curThmVecMapEntryVal = curThmVecMap.get(wordIndex);
				Integer queryVecMapVal = queryVecEntry.getValue();
				
				//use .equals(), and not simple reference equality.
				if(queryVecMapVal.equals(curThmVecMapEntryVal)){
					/**Note: Deliberately don't add if curThmVecMapEntryVal < 0, which cause lower-ranking results to
					 * bubble up if that result happened to not have a matching relation because it's less complex.*/
					/*if(curThmVecMapEntryVal < 0){
						//if only agree up to being the same universal or existential qualifier,
						//which is the case if < 0. Move to global constant!
						 * <--this leads
						numCoinciding++;					
					}*/
					if(curThmVecMapEntryVal > 0){
						numCoinciding += CONTEXT_MATCH_DEFAULT;
					}
				} 
				/**Note: Deliberately don't penalize result if mismatch instead of miss, 
				 * result could be still be useful, also negative will affect intersection search!*/
			}
			if(DEBUG) System.out.println("ContextSearch - index / numCoinciding " + thmIndex + " " + numCoinciding);
			if(numCoinciding < 0){
				continue;
			}
			/*List<Integer> curList = thmVecsTMap.get(numCoinciding);
			if(null != curList){
				curList.add(thmIndex);
			}else{
				curList = new ArrayList<Integer>();
				curList.add(thmIndex);
				thmVecsTMap.put(numCoinciding, curList);
			}*/
			contextVecScoreMap.put(thmIndex, numCoinciding);
		}
		
		//return thmVecsTMap;
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
			SearchState searchState = new SearchState();
			List<Integer> bestCommonVecs = contextSearchMap(thm, nearestVecList, null, searchState);
			
			/*for(int d : bestCommonVecs){
				System.out.println(TriggerMathThm2.getThm(d));
			}*/
		}
		
		sc.close();
		
	}
	
}
