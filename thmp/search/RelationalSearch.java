package thmp.search;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.TreeMultimap;

import thmp.parse.RelationVec;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Search using relation vectors, which represent relations
 * between terms, e.g. "... is A", "there exists ...".
 * Relation vectors are represented
 * using BigIntegers.
 * 
 * @see RelationVec.java. 
 * @author yihed
 */
public class RelationalSearch implements Searcher<Set<Integer>>{

	//private static final List<BigInteger> relationVecList;
	private static final Logger logger = LogManager.getLogger(RelationalSearch.class);
	private static final int SEARCH_SPAN_THRESHOLD = 3;
	private QueryVecContainer<Set<Integer>> searcherState;
	private static final int RELATION_SEARCH_NUM_NEAREST = 10;
	
	/*static{
		//should get vectors from deserialized ParsedExpression's List
		//relationVecList = CollectThm.ThmList.allThmsRelationVecList();		
	}*/
	
	@Override
	public void setSearcherState(QueryVecContainer<Set<Integer>> searcherState_){
		searcherState = searcherState_;
	}
	
	@Override
	public QueryVecContainer<Set<Integer>> getSearcherState(){
		return this.searcherState;
	}
	
	public static void main(String[] args){
		Scanner sc = new Scanner(System.in);
		
		//get all thms for now, to test 
		/*int allThmsListSz = CollectThm.ThmList.allThmsWithHypList().size();
		List<Integer> nearestVecList = new ArrayList<Integer>();
		for(int i = 0; i < allThmsListSz; i++){
			nearestVecList.add(i);
		}*/
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			if(WordForms.getWhiteEmptySpacePattern().matcher(thm).matches()) continue;
			
			thm = thm.toLowerCase();
			int NUM_NEAREST = RELATION_SEARCH_NUM_NEAREST;
			String queryVecStr = TriggerMathThm2.createQueryNoAnno(thm);
			List<Integer> nearestVecList = ThmSearch.ThmSearchQuery.findNearestVecs(queryVecStr, NUM_NEAREST);
			
			if(nearestVecList.isEmpty()){
				System.out.println("I've got nothing for you yet. Try again.");
				continue;
			}	
			SearchState searchState = new SearchState();
			List<Integer> bestCommonVecs = relationalSearch(thm, nearestVecList, null, searchState);			
			
			if(null == bestCommonVecs){
				System.out.println("No nontrivial relation found. Results from SVD displayed above.");
				continue;
			}
			
			int count = NUM_NEAREST;
			for(int d : bestCommonVecs){
				if(count < 1) break;
				System.out.println(ThmHypPairGet.retrieveThmHypPairWithThm(d));
				count--;
			}
		}
		
		sc.close();
		FileUtils.cleanupJVMSession();
	}
	
	/**
	 * Needed to implement Searcher.
	 */
	@Override
	public List<Integer> search(String queryStr, List<Integer> nearestThmIndexList, SearchState searchState){
		return relationalSearch(queryStr, nearestThmIndexList, this, searchState);
	}
	
	/**
	 * Return list of string's as opposed to indices. 
	 * Method used on servlet - June 2017.
	 * @param queryStr
	 * @param nearestThmIndexList
	 * @return
	 */
	public static List<ThmHypPair> getHighestThmStringList(String queryStr, List<Integer> nearestThmIndexList, 
			SearchState searchState){
		return SearchCombined.thmListIndexToThmHypPair(relationalSearch(queryStr, nearestThmIndexList, null, searchState));
	}
	
	/**
	 * Search based on relational vectors.
	 * @param queryStr
	 * @param nearestThmIndexList Ordered according to intersection search
	 * @return
	 */
	public static List<Integer> relationalSearch(String queryStr, List<Integer> nearestThmIndexList, 
			Searcher<Set<Integer>> searcher, SearchState searchState){
		
		if(nearestThmIndexList.isEmpty()) {
			return nearestThmIndexList;
		}
		Map<Integer, Integer> thmSpanMap = searchState.thmSpanMap();
		Integer firstThmSpan = thmSpanMap.get(nearestThmIndexList.get(0));
		if(null != firstThmSpan && firstThmSpan < SEARCH_SPAN_THRESHOLD ) {
			return nearestThmIndexList;
		}
		
		//form context vector of query
		Set<Integer> queryRelationVec;		
		
		if(null == searcher){
			queryRelationVec = thmp.parse.GenerateRelationVec.generateRelationVec(queryStr, searchState);
		}else{
			//optimization to store vec in searcherState, 
			//to avoid repeatedly parsing the same query string, 
			//in case some other search algorithm is used for same query.
			QueryVecContainer<Set<Integer>> searcherState;			
			if(null == (searcherState = searcher.getSearcherState())){
				queryRelationVec = thmp.parse.GenerateRelationVec.generateRelationVec(queryStr, searchState);
				searcher.setSearcherState(new QueryVecContainer<Set<Integer>>(queryRelationVec));
			}else{
				queryRelationVec = searcherState.getQueryVec();
				if(null == queryRelationVec){
					//query string parsed already, but non nontrivial query vec produced.
					return nearestThmIndexList;
				}
			}
		}
		
		//System.out.println("--++++++queryRelationVec " + queryRelationVec);
		//skip relation search if no nontrivial relation found, i.e. no bit set.
		if(null == queryRelationVec || queryRelationVec.size() == 0){
			return nearestThmIndexList;
		}
		
		//keys are distances to query relationVector, values are indices in nearestThmIndexList.
		TreeMultimap<Integer, Integer> nearestThmTreeMMap = TreeMultimap.create();
		
		int nearestThmIndexListSz = nearestThmIndexList.size();
		
		for(int i = 0; i < nearestThmIndexListSz; i++){
			int thmIndex = nearestThmIndexList.get(i);
			ContextRelationVecPair vecPair = TheoremGet.getContextRelationVecFromIndex(thmIndex);
			Set<Integer> relationVec_i = vecPair.relationVec();
			//relationVecList.add(relationVec_i);
			//if(true) throw new IllegalStateException();
			int hammingDistance_i = RelationVec.hammingDistanceForSets(queryRelationVec, relationVec_i);
			nearestThmTreeMMap.put(hammingDistance_i, thmIndex);
		}		
		return new ArrayList<Integer>(nearestThmTreeMMap.values());		
	}
	
	//this should be inside RelationVec!!
	@Deprecated
	private static int hammingDistance(BitSet bs1, BitSet bs2){
		BitSet bs1Copy = (BitSet)bs1.clone();
		BitSet bs2Copy = (BitSet)bs2.clone();
		
		bs1Copy.flip(0, bs1.size()); 
		bs2Copy.andNot(bs1Copy);
		//only the bits set in original bs1 are selected in bs2Copy.
		bs2Copy.xor(bs1);
		return bs2Copy.cardinality();
	}
	
	
}
