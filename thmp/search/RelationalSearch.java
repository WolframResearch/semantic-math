package thmp.search;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

import com.google.common.collect.TreeMultimap;

import thmp.GenerateRelationVec;
import thmp.ParseState;
import thmp.ParseState.ParseStateBuilder;
import thmp.utils.WordForms;

/**
 * Search using relation vectors, which are represented
 * using BitSets. 
 * See RelationVec.java. 
 * @author yihed
 *
 */
public class RelationalSearch {

	private static final List<BitSet> relationVecList;
	
	static{
		relationVecList = GenerateRelationVec.getRelationVecList();
	}
	
	public static List<Integer> relationalSearch(String queryStr, List<Integer> nearestThmIndexList){
		//short-circuit if query contains fewer than 3 words, so context doesn't make much sense	
		
		//short-circuit if context vec not meaninful (insignificant entries created)
		
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		ParseState parseState = parseStateBuilder.build();
		//form context vector of query
		BitSet queryRelationVec = thmp.GenerateRelationVec.generateRelationVec(queryStr, parseState);
		
		//keys are distances to query relationVector, values are indices in nearestThmIndexList.
		TreeMultimap<Integer, Integer> nearestThmTreeMMap = TreeMultimap.create();
		
		int nearestThmIndexListSz = nearestThmIndexList.size();
		
		for(int i = 0; i < nearestThmIndexListSz; i++){
			int thmIndex = nearestThmIndexList.get(i);
			BitSet relationVec_i = relationVecList.get(thmIndex);
			relationVecList.add(relationVec_i);
			int hammingDistance_i = hammingDistance(queryRelationVec, relationVec_i);
			nearestThmTreeMMap.put(hammingDistance_i, thmIndex);
		}
		
		return new ArrayList<Integer>(nearestThmTreeMMap.values());
		
	}
	
	//this should be inside RelationVec!!
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
