package thmp;

import java.io.Serializable;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Multimap;

import thmp.ThmP1.ParsedPair;
import thmp.WLCommand.PosTerm;
import thmp.search.TriggerMathThm2;

/**
 * Relational vectors used for search. Similar
 * to context vectors, but emphasizes the few
 * key relations, e.g. "A is", "is A", "if A",
 * "exists A",
 * Each relation occupies one segment of the total 
 * relation vec, so 5 relations -> 5 * total number of 
 * terms (rows) in term-document matrix. So must be judicious
 * when adding new relations. 
 * Vector is represented as a BitSet.
 * **What about other verbs and actions, e.g. 
 * "f maps X to Y", "A acts on B".
 * //context vector that takes into account structure of parse tree, i.e.
	//the relations between different Structs.
 * @author yihed
 *
 */
public class RelationVec implements Serializable{

	private static final long serialVersionUID = 7990758362732085287L;
	
	private static final int parseContextVectorSz = TriggerMathThm2.keywordDictSize();
	private static final Map<String, Integer> keywordDict = TriggerMathThm2.keywordDict();
	
	//should be wrapper around bitsets and make the bitsets immutable!!
	
	/**
	 * Enum for the different types of
	 * relations, such as "is A", "A is",
	 * along with their offset f.
	 */
	public static enum RelationType{
		
		//_IS: "A is", IS_: "is A".  
		_IS(0), IS_(1), IF(2), EXIST(3), NONE(-1);
		//must correspond to total number of relations above with offset > -1.
		private static final int totalRelationsCount = 4;
		
		//offset for how many times the total number of terms
		//in term-document matrix the segment for this type starts.
		private int vectorOffset;
		
		private RelationType(int offset){
			this.vectorOffset = offset;
		}
		
		public static int totalRelationsCount(){
			return totalRelationsCount;
		}
		
		/**
		 * @return offset for how many times the total number of terms
		 * in term-document matrix the segment for this type starts.
		 */
		public int vectorOffset(){
			return this.vectorOffset;
		}
		
	}
	
	/**
	 * Builds relation vec from ParsedPair's in parsedPairMMap, 
	 * @param parsedPairMMap
	 */
	public static BitSet buildRelationVec(Multimap<ParseStructType, ParsedPair> parsedPairMMap){
		
		//set of indices of set bits in the final bit vector.
		//Set<Integer> bitsIndexSet = new HashSet<Integer>();
		//bit vector whose set indices at particular words indicate the relations 
		//these words play in context. 
		//BitSet indexBitSet = new BitSet(parseContextVectorSz * RelationType.totalRelationsCount());
		BitSet indexBitSet = new BitSet();
		
		for(Map.Entry<ParseStructType, ParsedPair> pairEntry : parsedPairMMap.entries()){
			
			ParseStructType parseStructType = pairEntry.getKey();
			ParsedPair parsedPair = pairEntry.getValue();
			WLCommand wlCommand = parsedPair.getWlCommand();
			//wlCommand could be null, e.g. if no full parse.
			if(null == wlCommand){ 
				continue;			
			}
			List<PosTerm> posList = WLCommand.posTermList(wlCommand);
			
			boolean isParseStructTypeHyp = (parseStructType == ParseStructType.HYP
					|| parseStructType == ParseStructType.HYP_iff);
			
			for(PosTerm posTerm : posList){
				RelationType posTermRelationType = posTerm.relationType();
				if(RelationType.NONE != posTermRelationType){
					
					String contentStr = posTerm.posTermStruct().contentStr();
					//modulus and residue as used in abstract algebra.
					int modulus = posTermRelationType.vectorOffset();
	
					setBitVector(contentStr, indexBitSet, modulus, posTermRelationType, isParseStructTypeHyp);					
				}
			}
			
		}
		//if(true) throw new IllegalStateException(indexBitSet.toString());
		return indexBitSet;		
	}
	
	/**
	 * Auxilliary method for buildRelationVec() to set bits in BitSet.
	 * @param termStr The input string. 
	 * @param modulus Which segment of the index the current RelationType corresponds to.
	 */
	private static void setBitVector(String termStr, BitSet indexBitSet, int modulus, 
			RelationType posTermRelationType, boolean isParseStructTypeHyp){
		
		String[] termStrAr = termStr.split(" ");		
		int termStrArLen = termStrAr.length;
		
		if(termStrArLen > 1){
			//set indices for all terms in compound words, 
			//e.g. "... is regular local", sets "is regular, *and* "is local"
			for(int i = 0; i < termStrArLen; i++){
				
				String word = termStrAr[i];
				Integer residue = keywordDict.get(word);
				if(null == residue){
					continue;
				}
				indexBitSet.set(parseContextVectorSz*modulus + residue);

				//if parseStructType is HYP, also add to the "IF" segment.
				//e.g. "if $f$ is a surjection", should add to "if" segment
				//besides "IS_" and "_IS" segments.
				if(RelationType.IF != posTermRelationType && isParseStructTypeHyp){
					indexBitSet.set(RelationType.IF.vectorOffset()*modulus + residue);
				}
			}
		}
		
		//repeat for entire termStr:
		Integer residue = keywordDict.get(termStr);	
		if(null != residue){
			indexBitSet.set(parseContextVectorSz*modulus + residue);
	
			//if parseStructType is HYP, also add to the "IF" segment.
			//e.g. "if $f$ is a surjection", should add to "if" segment
			//besides "IS_" and "_IS" segments.
			if(RelationType.IF != posTermRelationType && isParseStructTypeHyp){
				indexBitSet.set(RelationType.IF.vectorOffset()*modulus + residue);
			}
		}
	}
	
	
}
