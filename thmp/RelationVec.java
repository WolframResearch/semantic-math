package thmp;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
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

	private static final int NUM_BITS_PER_BYTE = 8;
	
	//should be wrapper around bitsets and make the bitsets immutable!!
	
	/**
	 * Enum for the different types of
	 * relations, such as "is A", "A is",
	 * along with their offset. Example: _IS.
	 */
	public static enum RelationType{
		
		//_IS: "A is", IS_: "is A".
		//_IS_ means both _IS and IS_.
		_IS(new int[]{0}), IS_(new int[]{1}), _IS_(new int[]{0,1}), 
		IF(new int[]{2}), EXIST(new int[]{3}), NONE(new int[]{-1});
		//must correspond to total number of relations above with offset > -1.
		private static final int totalRelationsCount = 4;
		
		//offset for how many times the total number of terms
		//in term-document matrix the segment for this type starts.
		private int[] vectorOffsetArray;
		
		private RelationType(int[] offsetAr){
			this.vectorOffsetArray = offsetAr;
		}
		
		public static int totalRelationsCount(){
			return totalRelationsCount;
		}
		
		/**
		 * @return offset for how many times the total number of terms
		 * in term-document matrix the segment for this type starts.
		 */
		public int[] vectorOffsetArray(){
			return this.vectorOffsetArray;
		}
		
	}
	
	/**
	 * Builds relation vec from ParsedPair's in parsedPairMMap, 
	 * @param parsedPairMMap
	 * @return a BigInteger with the bits set.
	 */
	public static BigInteger buildRelationVec(Multimap<ParseStructType, ParsedPair> parsedPairMMap){
		
		//bit vector whose set bits at particular words indicate the relations 
		//these words play in context. bitPosList lists the bits to be set.
		int maxBitPos = 0;
		List<Integer> bitPosList = new ArrayList<Integer>();
		
		for(Map.Entry<ParseStructType, ParsedPair> pairEntry : parsedPairMMap.entries()){
			
			ParseStructType parseStructType = pairEntry.getKey();
			ParsedPair parsedPair = pairEntry.getValue();
			WLCommand wlCommand = parsedPair.getWlCommand();
			//wlCommand could be null, e.g. if no full parse.
			if(null == wlCommand){ 
				continue;			
			}
			/////
			boolean isParseStructTypeHyp = (parseStructType == ParseStructType.HYP
					|| parseStructType == ParseStructType.HYP_iff);

			maxBitPos = fillByteArrayFromCommand(wlCommand, isParseStructTypeHyp,
					maxBitPos, bitPosList);			
			
			//go through the composited commands, as they contain relational data as well.
			for(WLCommand compositedWLCommand : wlCommand.composedWLCommandsList()){
				maxBitPos = fillByteArrayFromCommand(compositedWLCommand, isParseStructTypeHyp,
						maxBitPos, bitPosList);			
			}
		}
		
		int byteArrayLength = maxBitPos/NUM_BITS_PER_BYTE;
		//byte array with capacity that corresponds to the position of the largest set bit.
		//big-endian, so more significant byte on the right. Add 1 to accomodate the remainder
		//left after division by 8.
		byte[] byteArray = new byte[byteArrayLength + 1];
		
		System.out.println("*&&&&&&& In RelationVec.java, positions of bits to be set: " + bitPosList);
		//fill in byteArray given the list of positions of bits to set
		fillByteArray(byteArray, bitPosList);
		BigInteger indexBitBigInt = new BigInteger(1, byteArray);
		//if(true) throw new IllegalStateException(indexBitSet.toString());
		return indexBitBigInt;		
	}
	
	/**
	 * Auxiliary method to find bit positions from WLCommand.
	 * @param wlCommand
	 * @param isParseStructTypeHyp
	 * @param maxBitPos
	 * @param bitPosList
	 * @return
	 */
	private static int fillByteArrayFromCommand(WLCommand wlCommand, boolean isParseStructTypeHyp,
			int maxBitPos, List<Integer> bitPosList){

		List<PosTerm> posList = WLCommand.posTermList(wlCommand);
		
		for(PosTerm posTerm : posList)
		{
			Struct posTermStruct = posTerm.posTermStruct();
			
			if(null == posTermStruct //&& posTerm.isOptionalTerm()
					){
				continue;
			}
			
			String contentStr = posTermStruct.contentStr();
			System.out.println("c&&&&&&&&&&&&&&&ontentStr: " + contentStr + " posTerm " + posTerm);
			
			List<RelationType> posTermRelationTypeList = posTerm.relationType();
			
			if(!posTermRelationTypeList.isEmpty())
			{					
				for(RelationType posTermRelationType : posTermRelationTypeList)
				{	
					//offset and residue (like remainder): num = offset * modulus + residue.
					int[] multiplicityAr = posTermRelationType.vectorOffsetArray();
					
					//add new indices to bitPosList
					for(int multiplicity : multiplicityAr){
						int curBitPos = setBitPosList(contentStr, bitPosList, multiplicity, posTermRelationType, isParseStructTypeHyp);
						if(curBitPos > maxBitPos){
							maxBitPos = curBitPos;
						}
					}
				}
			}
		}
		return maxBitPos;
	}
	
	/**
	 * Fill in byteArray given the list of positions of bits to set. 
	 * Auxiliary method to buildRelationVec().
	 * @param byteArray
	 * @param bitPosList
	 */
	private static void fillByteArray(byte[] byteArray, List<Integer> bitPosList) {
		
		//compute each byte and the position to place it.
		int byteArrayLen = byteArray.length;
		
		for(int bitPos : bitPosList){
			int modulus = bitPos/NUM_BITS_PER_BYTE;
			int residue = bitPos - modulus*NUM_BITS_PER_BYTE;
			byte curByte = (byte)(1<<residue);
			byteArray[byteArrayLen - 1 - modulus] = curByte;
		}		
	}
	
		/**
		 * Auxilliary method for buildRelationVec() to set bits in BitSet.
		 * @param termStr The input string. 
		 * @param modulus Which segment of the index the current RelationType corresponds to.
		 * @return max position this run in the new bit positions added.
		 */
		private static int setBitPosList(String termStr, List<Integer> bitPosList, int modulus, 
				RelationType posTermRelationType, boolean isParseStructTypeHyp){
			
			int maxBitPos = 0;
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
					
					int bitPos = parseContextVectorSz*modulus + residue;					
					maxBitPos = addToPosList(bitPosList, maxBitPos, bitPos);
					
					//if parseStructType is HYP, also add to the "IF" segment.
					//e.g. "if $f$ is a surjection", should add to "if" segment
					//besides "IS_" and "_IS" segments.
					if(RelationType.IF != posTermRelationType && isParseStructTypeHyp){
						int[] multiplicityAr = RelationType.IF.vectorOffsetArray();
						for(int multiplicity : multiplicityAr){
							bitPos = multiplicity*parseContextVectorSz + residue;
							maxBitPos = addToPosList(bitPosList, maxBitPos, bitPos);
						}
					}
				}
			}
		
		//repeat for the whole of termStr:
		Integer residue = keywordDict.get(termStr);
		//if(termStr.equals("injective")) throw new IllegalStateException(residue+"");
		
		if(null != residue){
			int bitPos = parseContextVectorSz*modulus + residue;
			maxBitPos = addToPosList(bitPosList, maxBitPos, bitPos);
			//if parseStructType is HYP, also add to the "IF" segment.
			//e.g. "if $f$ is a surjection", should add to "if" segment
			//besides "IS_" and "_IS" segments.
			if(RelationType.IF != posTermRelationType && isParseStructTypeHyp){
				int[] multiplicityAr = RelationType.IF.vectorOffsetArray();
				for(int multiplicity : multiplicityAr){
					bitPos = multiplicity*parseContextVectorSz + residue;
					maxBitPos = addToPosList(bitPosList, maxBitPos, bitPos);
				}
			}
		}
		return maxBitPos;
	}

		/**
		 * @param bitPosList
		 * @param maxBitPos
		 * @param bitPos
		 * @return
		 */
		private static int addToPosList(List<Integer> bitPosList, int maxBitPos, int bitPos) {
			bitPosList.add(bitPos);					
			if(bitPos > maxBitPos){
				maxBitPos = bitPos;
			}
			return maxBitPos;
		}
	
		//use xor rather than flip, to improved memory efficiency.
		/**
		 * Gives the number of set bits in bi2 that differ from the set bits
		 * in bi1, where we only consider bit positions that are set in bi1.
		 * @param bi1 The base vector. Should correspond to query vector.
		 * @param bi2
		 * @return
		 */
		public static int hammingDistance2(BigInteger bi1, BigInteger bi2){
			//first only restrict to bit positions in bi2 that are also set in bi1.
			//System.out.println("bi2: " + bi2);
			BigInteger bi = bi1.and(bi2);
			//this gives the number of set bits in bi2 that differ from the set bits
			//in bi1, where we only consider bit positions that are set in bi1.
			return bi.xor(bi1).bitCount();
		}
	
}
