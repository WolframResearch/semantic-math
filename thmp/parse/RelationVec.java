package thmp.parse;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.collect.Multimap;

import thmp.parse.ThmP1.ParsedPair;
import thmp.parse.WLCommand.PosTerm;
import thmp.search.CollectThm;
import thmp.search.Searcher;
import thmp.search.TriggerMathThm2;
import thmp.utils.GatherRelatedWords.RelatedWords;
import thmp.utils.WordForms.WordMapIndexPair;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

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

	//map of words and their indices.
	//private static final Map<String, Integer> contextKeywordIndexThmsDataDict = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_INDEX_MAP();
	
	//used for forming query vecs, as these are words used when the thm source vecs were formed, words and their indices.
	//Ordered according to frequency.
	private static final Map<String, Integer> contextKeywordIndexQueryDict = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_INDEX_MAP();
	/*Deliberately not final, since not needed, in fact not created, when gathering data instead of searching.
	 * We want the *precise* wording for the thms, not using related words. */
	private static Map<String, RelatedWords> relatedWordsMap;
	private static final boolean GATHERING_DATA_BOOL;
	/*The current word-index dictionary to use, this *must* be set to contextKeywordThmsDataDict 
	 * when producing vecs from thm data source. e.g. in DetectHypothesis.java. */
	private static final Map<String, Integer> keywordIndexDict;
	
	private static final int parseContextVectorSz;	
	private static final int NUM_BITS_PER_BYTE = 8;
	private static final Set<Integer> PLACEHOLDER_RELATION_VEC = Collections.<Integer>emptySet();//new BigInteger(new byte[]{0});
	
	private static final boolean DEBUG = FileUtils.isOSX() ? InitParseWithResources.isDEBUG() : false;
	
	static{
		if(Searcher.SearchMetaData.gatheringDataBool()){
			//Sets the dictionary to the mode for producing context vecs from data source to be searched.
			// e.g. in DetectHypothesis.java.
			//shoudl use same pre-computed map in both cases to ensure consistency!
			///keywordIndexDict = contextKeywordIndexThmsDataDict;
			GATHERING_DATA_BOOL = true;
		}else{
			///keywordIndexDict = contextKeywordIndexQueryDict;
			relatedWordsMap = CollectThm.ThmWordsMaps.getRelatedWordsMap();
			GATHERING_DATA_BOOL = false;
		}
		keywordIndexDict = contextKeywordIndexQueryDict;		
		parseContextVectorSz = keywordIndexDict.size();
	}
	/**
	 * Enum for the different types of
	 * relations, such as "is A", "A is",
	 * along with their offset. Example: _IS.
	 */
	public static enum RelationType{
		
		/*_IS: "A is", IS_: "is A".
		 * _IS_ means both _IS and IS_.*/ 
		_IS(new int[]{0}), IS_(new int[]{1}), _IS_(new int[]{0,1}), 
		IF(new int[]{2}), EXIST(new int[]{3}), NONE(new int[]{-1});
		//must correspond to total number of relations above with offset > -1.
		//but there are 4 not 5! <--change this next time all parsedExpressionList gets generated.
		//private static final int totalRelationsCount = 4;
		
		//offset for how many times the total number of terms
		//in term-document matrix (list of words used in relation vec) 
		//the segment for this type starts.
		private int[] vectorOffsetArray;
		
		private RelationType(int[] offsetAr){
			this.vectorOffsetArray = offsetAr;
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
	 * @return a set containing the bits set.
	 */
	public static Set<Integer> buildRelationVec(Multimap<ParseStructType, ParsedPair> parsedPairMMap){
		
		//bit vector whose set bits at particular words indicate the relations 
		//these words play in context. bitPosList lists the bits to be set.
		int maxBitPos = 0;
		Set<Integer> bitPosCol = new HashSet<Integer>();
		
		for(Map.Entry<ParseStructType, ParsedPair> pairEntry : parsedPairMMap.entries()){
			
			ParseStructType parseStructType = pairEntry.getKey();
			ParsedPair parsedPair = pairEntry.getValue();
			WLCommand wlCommand = parsedPair.getWlCommand();
			//wlCommand could be null, e.g. if no full parse.
			if(null == wlCommand){ 
				continue;			
			}
			
			boolean isParseStructTypeHyp = (parseStructType == ParseStructType.HYP
					|| parseStructType == ParseStructType.HYP_iff);
			
			maxBitPos = fillByteArrayFromCommand(wlCommand, isParseStructTypeHyp,
					maxBitPos, bitPosCol);			
			
			//go through the composited commands, as they contain relational data as well.
			for(WLCommand compositedWLCommand : wlCommand.composedWLCommandsList()){
				maxBitPos = fillByteArrayFromCommand(compositedWLCommand, isParseStructTypeHyp,
						maxBitPos, bitPosCol);			
			}
		}
		
		int byteArrayLength = maxBitPos/NUM_BITS_PER_BYTE;
		//byte array with capacity that corresponds to the position of the largest set bit.
		//big-endian, so more significant byte on the right. Add 1 to accomodate the remainder
		//left after division by 8.
		byte[] byteArray = new byte[byteArrayLength + 1];
		
		//fill in byteArray given the list of positions of bits to set
		/**fillByteArray(byteArray, bitPosCol);
		BigInteger indexBitBigInt = new BigInteger(1, byteArray); don't delete yet, Sept 18 2017*/
		return bitPosCol;		
	}
	
	/**
	 * Auxiliary method to find bit positions from WLCommand.
	 * @param wlCommand
	 * @param isParseStructTypeHyp
	 * @param maxBitPos
	 * @param bitPosSet
	 * @return
	 */
	private static int fillByteArrayFromCommand(WLCommand wlCommand, boolean isParseStructTypeHyp,
			int maxBitPos, Collection<Integer> bitPosSet){

		List<PosTerm> posList = WLCommand.posTermList(wlCommand);
		
		for(PosTerm posTerm : posList){
			Struct posTermStruct = posTerm.posTermStruct();			
			if(null == posTermStruct //&& posTerm.isOptionalTerm()
					){
				continue;
			}	
			//contentStrList contains contents of descendants as well.
			List<String> contentStrList = posTermStruct.contentStrList();
			//System.out.println("***********RelationVec - contentStrList: " + contentStrList + " posTerm: " + posTerm);
			
			List<RelationType> posTermRelationTypeList = posTerm.relationType();
			//System.out.println("****************RelationVec - posTermRelationTypeList: "  + posTermRelationTypeList);
			if(!posTermRelationTypeList.isEmpty())
			{					
				for(RelationType posTermRelationType : posTermRelationTypeList)
				{	
					//offset/multiplicity and residue (like remainder): num = multiplicity * modulus + residue.
					//modulus is the base, e.g. the prime 5 in the finite field Z/5Z.
					//MultiplicityAr gives the locations a term goes into all the slots 
					//used in the relation vector. 
					//e.g. "A" in "If A is B" has the offset for both "_IS" and "IF", 
					//so the multiplicityAr is e.g. [0, 2]
					int[] multiplicityAr = posTermRelationType.vectorOffsetArray();
					
					//add new indices to bitPosList
					for(int multiplicity : multiplicityAr){
						int curBitPos = setBitPosList(contentStrList, bitPosSet, multiplicity, 
								posTermRelationType, isParseStructTypeHyp);						
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
	 * @param bitPosSet
	 */
	private static void fillByteArray(byte[] byteArray, Collection<Integer> bitPosSet) {
		
		//compute each byte and the position to place it.
		int byteArrayLen = byteArray.length;
		
		for(int bitPos : bitPosSet){
			int modulus = bitPos/NUM_BITS_PER_BYTE;
			int residue = bitPos - modulus*NUM_BITS_PER_BYTE;
			byte curByte = (byte)(1<<residue);
			byteArray[byteArrayLen - 1 - modulus] = curByte;
		}		
	}
	
	/**
	 * Auxilliary method for buildRelationVec() to set bits in BitSet. Takes in list of Strings rather
	 * than a single String.
	 * @param modulus Which segment of the index the current RelationType corresponds to.
	 * @param termStrList List of Strings to be added to context vector, in which each String
	 * will be decomposed into pieces as well.
	 * @param bitPosCol
	 * @param posTermRelationType
	 * @param isParseStructTypeHyp
	 * @return  max position this run in the new bit positions added.
	 */
	private static int setBitPosList(List<String> termStrList, Collection<Integer> bitPosCol, int modulus, 
			RelationType posTermRelationType, boolean isParseStructTypeHyp){
		
		int maxBitPos = 0;
		for(String termStr : termStrList){
			int curMax = setBitPosList(termStr, bitPosCol, modulus, posTermRelationType, isParseStructTypeHyp);
			if(curMax > maxBitPos) maxBitPos = curMax;
		}
		return maxBitPos;
	}
	
		/**
		 * Auxilliary method for buildRelationVec() to set bits in BitSet.
		 * termStr should already been singularized, since it comes from parse.
		 * @param termStr The input string. 
		 * @param modulus Which segment of the index the current RelationType corresponds to, e.g. _IS.
		 * @return max position this run in the new bit positions added.
		 */
		private static int setBitPosList(String termStr, Collection<Integer> bitPosSet, int modulus, 
				RelationType posTermRelationType, boolean isParseStructTypeHyp){
			
			if("".equals(termStr)) return 0;
			
			int maxBitPos = 0;
			List<String> termStrList = WordForms.splitThmIntoSearchWordsList(termStr);
			int termStrArLen = termStrList.size();
			
			if(termStrArLen > 1){
				//set indices for all terms in compound words, 
				//e.g. "... is regular local", sets "is regular, *and* "is local"
				for(int i = 0; i < termStrArLen; i++){			
					String word = termStrList.get(i);
					
					List<String> relatedWordsList = null;
					RelatedWords relatedWords;
					if(!GATHERING_DATA_BOOL){
						//add related words
						relatedWords = relatedWordsMap.get(word);
						if(null != relatedWords){
							relatedWordsList = relatedWords.getCombinedList();
						}
					}
					
					//System.out.println("RelationVec.java - trying to add word " + word);					
					WordMapIndexPair pair = WordForms.uniformizeWordAndGetIndex(word, keywordIndexDict);	
					Integer residue = pair.mapIndex();// = keywordIndexDict.get(word);
					if(WordMapIndexPair.placeholderWordMapIndexPair() != pair){
						//String normalizedWord = WordForms.normalizeWordForm(word);
						//residue = keywordIndexDict.get(normalizedWord);
						if(!GATHERING_DATA_BOOL){
							if(null == relatedWordsList){
								relatedWords = relatedWordsMap.get(pair.word());
								if(null != relatedWords){
									relatedWordsList = relatedWords.getCombinedList();
								}
							}
							//add the residues for related words first
							maxBitPos = addRelatedWordsResidue(bitPosSet, modulus, posTermRelationType, isParseStructTypeHyp,
									maxBitPos, relatedWordsList);
						}
					}else{
						if(!GATHERING_DATA_BOOL){
							//add the residues for related words first
							maxBitPos = addRelatedWordsResidue(bitPosSet, modulus, posTermRelationType, isParseStructTypeHyp,
									maxBitPos, relatedWordsList);
						}
						continue;
					}
					if(DEBUG){
						System.out.println("RelationVec - adding word " + word);
					}
					int bitPos = parseContextVectorSz*modulus + residue;					
					maxBitPos = addToPosList(bitPosSet, maxBitPos, bitPos);					
					//if parseStructType is HYP, also add to the "IF" segment.
					//e.g. "if $f$ is a surjection", should add to "if" segment
					//besides "IS_" and "_IS" segments.
					maxBitPos = addHypRelationType(residue, bitPosSet, maxBitPos,
							posTermRelationType, isParseStructTypeHyp);
				}
			}
		
			//repeat for the whole of termStr:
			Integer residue = keywordIndexDict.get(termStr);
			List<String> relatedWordsList = null;
			RelatedWords relatedWords = null; 
			
			if(!GATHERING_DATA_BOOL){
				relatedWords = relatedWordsMap.get(termStr);				
				if(null != relatedWords){
					relatedWordsList = relatedWords.getCombinedList();
				}
			}
			if(null == residue){
				String normalizedTermStr = WordForms.normalizeWordForm(termStr);
				residue = keywordIndexDict.get(normalizedTermStr);
				if(!GATHERING_DATA_BOOL){
					if(null == relatedWordsList){
						relatedWords = relatedWordsMap.get(normalizedTermStr);
						if(null != relatedWords){
							relatedWordsList = relatedWords.getCombinedList();
						}
					}
				}
			}			
			if(!GATHERING_DATA_BOOL){
				maxBitPos = addRelatedWordsResidue(bitPosSet, modulus, posTermRelationType, isParseStructTypeHyp,
						maxBitPos, relatedWordsList);
			}
			if(null != residue){
				if(DEBUG){
					System.out.println("RelationVec - adding word " + termStr);
				}
				int bitPos = parseContextVectorSz*modulus + residue;
				maxBitPos = addToPosList(bitPosSet, maxBitPos, bitPos);
				//if parseStructType is HYP, also add to the "IF" segment.
				//e.g. "if $f$ is a surjection", should add to "if" segment
				//besides "IS_" and "_IS" segments.
				maxBitPos = addHypRelationType(residue, bitPosSet, maxBitPos,
						posTermRelationType, isParseStructTypeHyp);
			}
			return maxBitPos;
		}

		/**
		 * @param bitPosSet
		 * @param modulus
		 * @param posTermRelationType
		 * @param isParseStructTypeHyp
		 * @param maxBitPos
		 * @param relatedWordsList
		 * @return
		 */
		public static int addRelatedWordsResidue(Collection<Integer> bitPosSet, int modulus,
				RelationType posTermRelationType, boolean isParseStructTypeHyp, int maxBitPos,
				List<String> relatedWordsList) {
			if(null != relatedWordsList){
				for(String relatedWord : relatedWordsList){
					Integer relatedWordResidue = keywordIndexDict.get(relatedWord);
					if(null != relatedWordResidue){
						int bitPos = parseContextVectorSz*modulus + relatedWordResidue;					
						maxBitPos = addToPosList(bitPosSet, maxBitPos, bitPos);
						maxBitPos = addHypRelationType(relatedWordResidue, bitPosSet, maxBitPos,
								posTermRelationType, isParseStructTypeHyp);
					}
				}						
			}
			return maxBitPos;
		}
		
		/**
		 * Adds residues at corresponding places for "if" RelationType's, besides e.g. _IS.
		 * @param residue
		 * @param bitPosCol
		 * @param maxBitPos
		 * @param posTermRelationType
		 * @param isParseStructTypeHyp
		 * @return
		 */
		private static int addHypRelationType(int residue, Collection<Integer> bitPosCol, int maxBitPos,
				RelationType posTermRelationType, boolean isParseStructTypeHyp){
			if(RelationType.IF != posTermRelationType && isParseStructTypeHyp){
				int[] multiplicityAr = RelationType.IF.vectorOffsetArray();
				for(int multiplicity : multiplicityAr){
					int bitPos = multiplicity*parseContextVectorSz + residue;
					maxBitPos = addToPosList(bitPosCol, maxBitPos, bitPos);
				}
			}
			return maxBitPos;
		}
		
		/**
		 * @param bitPosCol
		 * @param maxBitPos
		 * @param bitPos
		 * @return
		 */
		private static int addToPosList(Collection<Integer> bitPosCol, int maxBitPos, int bitPos) {
			bitPosCol.add(bitPos);					
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

		/**
		 * Gives the number of set bits in bi2 that differ from the set bits
		 * in bi1, where we only consider bit positions that are set in bi1.
		 * @param bi1 The base vector. Should correspond to query vector.
		 * @param bi2
		 * @return
		 */
		public static int hammingDistanceForSets(Set<Integer> querySet, Set<Integer> thmSet){
			//first only restrict to bit positions in bi2 that are also set in bi1.
			int dist = 0;
			for(int queryBit : querySet) {
				if(!thmSet.contains(queryBit)) {
					dist++;
				}
			}			
			//this gives the number of set bits in bi2 that differ from the set bits
			//in bi1, where we only consider bit positions that are set in bi1.
			return dist;
		}
		
		public static Set<Integer> getPlaceholderRelationVec() {
			return PLACEHOLDER_RELATION_VEC;
		}
	
}
