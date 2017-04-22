package thmp.search;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.TreeMultimap;

import thmp.GenerateRelationVec;
import thmp.ParseState;
import thmp.ParseState.ParseStateBuilder;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.Searcher.SearcherState;
import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.RelationVec;
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
public class RelationalSearch implements Searcher<BigInteger>{

	//private static final List<BigInteger> relationVecList;
	private static final Logger logger = LogManager.getLogger(RelationalSearch.class);
	private SearcherState<BigInteger> searcherState;
	
	static{
		//should get vectors from deserialized ParsedExpression's List
		//relationVecList = CollectThm.ThmList.allThmsRelationVecList();		
	}
	
	@Override
	public void setSearcherState(SearcherState<BigInteger> searcherState_){
		searcherState = searcherState_;
	}
	
	@Override
	public SearcherState<BigInteger> getSearcherState(){
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
			int NUM_NEAREST = 6;
			String queryVecStr = TriggerMathThm2.createQueryNoAnno(thm);
			List<Integer> nearestVecList = ThmSearch.ThmSearchQuery.findNearestVecs(queryVecStr, NUM_NEAREST);
			
			if(nearestVecList.isEmpty()){
				System.out.println("I've got nothing for you yet. Try again.");
				continue;
			}			
			List<Integer> bestCommonVecs = relationalSearch(thm, nearestVecList, null);			
			
			if(null == bestCommonVecs){
				System.out.println("No nontrivial relation found. Results from SVD displayed above.");
				continue;
			}
			
			int count = NUM_NEAREST;
			for(int d : bestCommonVecs){
				if(count < 1) break;
				System.out.println(TriggerMathThm2.getThm(d));
				count--;
			}
		}
		
		sc.close();
		FileUtils.cleanupJVMSession();
	}
	
	/**
	 * Needed to implement Searcher.
	 */
	public List<Integer> search(String queryStr, List<Integer> nearestThmIndexList){
		return relationalSearch(queryStr, nearestThmIndexList, this);
	}
	
	/**
	 * Return list of string's as opposed to indices.
	 * @param queryStr
	 * @param nearestThmIndexList
	 * @return
	 */
	public static List<ThmHypPair> getHighestThmStringList(String queryStr, List<Integer> nearestThmIndexList){
		return SearchCombined.thmListIndexToThmHypPair(relationalSearch(queryStr, nearestThmIndexList, null));
	}
	
	/**
	 * Search based on relational vectors.
	 * @param queryStr
	 * @param nearestThmIndexList
	 * @return
	 */
	public static List<Integer> relationalSearch(String queryStr, List<Integer> nearestThmIndexList, 
			Searcher<BigInteger> searcher){
		//short-circuit if query contains fewer than 3 words, so context doesn't make much sense	
		
		//short-circuit if context vec not meaningful (insignificant entries created)
		
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		ParseState parseState = parseStateBuilder.build();
		//form context vector of query
		BigInteger queryRelationVec;		
		
		if(null == searcher){
			queryRelationVec = thmp.GenerateRelationVec.generateRelationVec(queryStr, parseState);
		}else{
			//optimization to store vec in searcherState, 
			//to avoid repeatedly parsing the same query string.
			SearcherState<BigInteger> searcherState;
			
			if(null == (searcherState = searcher.getSearcherState())){
				queryRelationVec = thmp.GenerateRelationVec.generateRelationVec(queryStr, parseState);
				searcher.setSearcherState(new SearcherState<BigInteger>(queryRelationVec));
			}else{
				queryRelationVec = searcherState.getQueryVec();
				if(null == queryRelationVec){
					//query string parsed already, but non nontrivial query vec produced.
					return nearestThmIndexList;
				}
			}
		}
		
		System.out.println("--++++++queryRelationVec " + queryRelationVec);
		//skip relation search if no nontrivial relation found, i.e. no bit set.
		if(null == queryRelationVec || queryRelationVec.bitCount() == 0){
			return nearestThmIndexList;
		}
		
		//keys are distances to query relationVector, values are indices in nearestThmIndexList.
		TreeMultimap<Integer, Integer> nearestThmTreeMMap = TreeMultimap.create();
		
		int nearestThmIndexListSz = nearestThmIndexList.size();
		
		for(int i = 0; i < nearestThmIndexListSz; i++){
			int thmIndex = nearestThmIndexList.get(i);
			ContextRelationVecPair vecPair = TheoremGet.getContextRelationVecFromIndex(thmIndex);
			BigInteger relationVec_i = vecPair.relationVec();
			//relationVecList.add(relationVec_i);
			//if(true) throw new IllegalStateException();
			int hammingDistance_i = RelationVec.hammingDistance2(queryRelationVec, relationVec_i);
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
