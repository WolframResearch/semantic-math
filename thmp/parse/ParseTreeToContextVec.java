package thmp.parse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.collect.ListMultimap;

import thmp.parse.ParseToWLTree.WLCommandWrapper;
import thmp.parse.Struct.NodeType;
import thmp.parse.WLCommand.PosTerm;
import thmp.search.CollectThm;
import thmp.search.Searcher;
import thmp.search.TriggerMathThm2;
import thmp.utils.GatherRelatedWords.RelatedWords;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;
import thmp.utils.WordForms.WordMapIndexPair;

/**
 * Converts a parse tree produced by ThmP1 
 * into a context vector, where the rows correspond 
 * to terms of the term-document matrix.
 * 
 * @author yihed
 */
public class ParseTreeToContextVec {

	//private static final Map<String, Integer> contextKeywordDict = TriggerMathThm2.allThmsKeywordIndexDict();
	//should ideally order the words according to relative distances apart, i.e. entries in the correlation matrix.
	//used for forming query vecs, as these are words used when the thm source vecs were formed.
	/* Map of words and their *indices* */
	//private static final Map<String, Integer> contextKeywordIndexQueryDict = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_INDEX_MAP();
	//contextVecWordsNextTimeMMap
	private static final Map<String, Integer> contextKeywordIndexThmsDataDict = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_INDEX_MAP();
	//.get_contextVecWordsIndexNextTimeMap();
	private static final boolean DEBUG = FileUtils.isOSX() ? InitParseWithResources.isDEBUG() : false;
	/*The current word-index map to use, this *must* be set to contextKeywordIndexThmsDataDict when producing vecs from thm data source. 
	* e.g. in DetectHypothesis.java. */
	private static final Map<String, Integer> contextKeywordIndexDict;
	/*Deliberately not final, since not needed, in fact not created, when gathering data instead of searching.*/
	private static Map<String, RelatedWords> relatedWordsMap;
	//private static final Map<String, Integer> contextKeywordDict = ;
	private static final ListMultimap<String, String> posMMap = Maps.posMMap();
	//threshold for index when it is meaningful
	private static final int INDEX_MEANING_THRESHOLD = 0;
	private static final boolean GATHERING_DATA_BOOL;
	private static final Pattern TEX_PATTERN = Pattern.compile("\\$[^$]+\\$");
	
	static{
		
		contextKeywordIndexDict = contextKeywordIndexThmsDataDict;
		if(Searcher.SearchMetaData.gatheringDataBool()){
			/* Sets the dictionary to the mode for producing context vecs from data source to be searched.
			 * e.g. in DetectHypothesis.java. */			
			GATHERING_DATA_BOOL = true;
		}else{
			relatedWordsMap = CollectThm.ThmWordsMaps.getRelatedWordsMap();
			GATHERING_DATA_BOOL = false;
		}
		//System.out.println("contextKeywordDict " + contextKeywordIndexThmsDataDict);
	}
	/**
	 * Sets the dictionary to the mode for producing context vecs from data source to be searched.
	 * e.g. in DetectHypothesis.java.
	 */
	/*public static void set_contextKeywordDictToDataMode(){
		contextKeywordDict = contextKeywordThmsDataDict;
	}*/
	
	/**
	 * Entry method from ParseToWLTree, sets the term corresponding to head using WLCommandStr. 
	 * calls tree2vec.
	 * @param headStruct
	 * @param contextVec
	 * @return
	 */
	public static Map<Integer, Integer> tree2vec(Struct headStruct, Map<Integer, Integer> contextVecMap){
		return tree2vec(headStruct, contextVecMap, null);
	}
	
	/**
	 * Entry method from ParseToWLTree, sets the term corresponding to head using WLCommandStr. 
	 * calls tree2vec.
	 * @param headStruct
	 * @param contextVec
	 * @param WLCommandStr
	 * @return contextVec filled with indices.
	 */
	public static Map<Integer, Integer> tree2vec(Struct headStruct, Map<Integer, Integer> contextVecMap, WLCommandWrapper commandWrapper){
		// curCommand's type determines the num attached to head of structH,
		// e.g. "Exists[structH]" writes enum ParseRelation -2 at the index of structH 
		//get the entry for head and sets it corresponding to the term for headStruct
		ParseRelation parseRelation = null;
		int headVecEntry;
		if(commandWrapper != null){			
			parseRelation = ParseRelation.getParseRelation(headStruct, commandWrapper.WLCommandStr());
			headVecEntry = parseRelation.relationNum;
			//System.out.println("HERE (in ParseTreeToVec)" + commandWrapper == null ? "" : commandWrapper.WLCommandStr());
		}else{
			headVecEntry = ParseRelation.DEFAULT_RELATION_NUM();
			//System.out.println("HERE (in ParseTreeToVec)");
		}
		//System.out.println("parseRelation headVecEntry " + headVecEntry);
		//System.out.println("headStruct " + headStruct);
		parseRelation = parseRelation == null ? ParseRelation.DEFAULT : parseRelation;
		
		tree2vec(headStruct, headVecEntry, contextVecMap);
		//adjust the context vector, to account for e.g. A \[Element] B
		if(commandWrapper != null){
			ParseRelation.adjustContextVec(headStruct, contextVecMap, commandWrapper, parseRelation);
		}
		return contextVecMap;
	}
	
	/**
	 * Convert tree to vector. Intermediate method, uses dynamic dispatch to call setContextVecEntry()
	 * on either StructA or StructH, which in turn calls setStructHContextVecEntry().
	 * @param headStruct head Struct to which current WLCommandStr is attached to.
	 * @param headStructRowIndex is index of parent (of headStruct).
	 * @param contextVec current context vector containing indices for terms in parsed string.
	 */
	private static Map<Integer, Integer> tree2vec(Struct headStruct, int headStructParentIndex, Map<Integer, Integer> contextVecMap){
		
		//sets the entry correponding to headStruct
		//setContextVecEntry(headStruct, contextVec, headStructRowIndex);
		//call the applicable method in either StructA or StructH, with dynamic dispatch
		boolean adjustVecFromCommand = true;
		headStruct.setContextVecEntry(headStructParentIndex, contextVecMap, adjustVecFromCommand);
		return contextVecMap;
	}

	/**
	 * Double dispatch, called from StructH, to set the context vector. 
	 * @param struct
	 * @param contextVec
	 * @param structParentIndex row index of parent of struct
	 * @param whether to adjust the vect based on WLCommand, false if this is already adjustment.
	 * @return
	 */
	public static void setStructHContextVecEntry(StructH<?> struct, int structParentIndex, Map<Integer, Integer> contextVecMap, 
			boolean adjustVecFromCommand){
		//termsStr only contains name of struct, properties are considered below 
		String termStr = struct.nameStr();
		int structIndex = INDEX_MEANING_THRESHOLD;
		
		//add struct itself, then its children
		//don't count parent is parent just all latex expressions
		if(!TEX_PATTERN.matcher(termStr).matches()){
			structIndex = setContextVecEntry(struct, termStr, structParentIndex, contextVecMap);
		}
		
		collectAssertContextVec(struct, contextVecMap, structIndex);
		collectVPContextVec(struct, contextVecMap, structIndex);
		//System.out.println("***inside StructH context vec setting, struct " + struct + " parentIndex " + structParentIndex);
		List<Struct> children = struct.children();
		
		for(Struct child : children){
			//System.out.println("child! " + child + " ||| parent! " + struct);
			tree2vec(child, structIndex, contextVecMap);
		}
		
		//should also set indices of ppt's of StructH, e.g. "maximal ideal", maximal is ppt of ideal 
		Set<String> propertySet = struct.getPropertySet();
		//mark indices for words that are properties
		for(String propertyStr : propertySet){		
			setContextVecEntry(struct, propertyStr, structIndex, contextVecMap);
			//separate here instead of in setContextVecEntry, make all  point to the ent			
		}
		if(adjustVecFromCommand){
			adjustFromWrapper(struct, contextVecMap);
		}
	}
	
	/**
	 * Double dispatch, called from StructH, to set the context vector. 
	 * @param struct
	 * @param contextVec
	 * @param structParentIndex row index of parent of struct
	 * @return
	 */
	public static void setStructAContextVecEntry(StructA<?, ?> struct, int structParentIndex, Map<Integer, Integer> contextVecMap, 
			boolean adjustVecFromCommand){
		
		List<String> termStrList = struct.contentStrCurLevelList();
		//be more specific, "algebraic closure", want "algebraic" to point to "closure", so "closure" is parent
		//of "algebraic". Consistent with "closure is algebraic" => A \[Elememt] B, B describes A, so points to A.

		//add the Strings for struct itself, then its prev1 and prev2
		int structIndex = structParentIndex;
		//String structType = struct.type();
		int termStrListLen = termStrList.size();
		if(termStrListLen > 0){
			structIndex = setContextVecEntry(struct, termStrList.get(0), structParentIndex, contextVecMap);
		}
		for(int i = 1; i < termStrListLen; i++){
			setContextVecEntry(struct, termStrList.get(i), structParentIndex, contextVecMap);
		}
		collectAssertContextVec(struct, contextVecMap, structIndex);
		collectVPContextVec(struct, contextVecMap, structIndex);
		//do prev1 and prev2
		if(struct.prev1NodeType().isTypeStruct()){
			tree2vec((Struct)(struct.prev1()), structIndex, contextVecMap);
		}
		
		if(struct.prev2NodeType().isTypeStruct()){
			tree2vec((Struct)(struct.prev2()), structIndex, contextVecMap);
		}

		List<Struct> children = struct.children();		
		for(Struct child : children){
			tree2vec(child, structIndex, contextVecMap);
		}
		if(adjustVecFromCommand){
			adjustFromWrapper(struct, contextVecMap);
		}	
	}

	/**
	 * @param struct
	 * @param contextVecMap
	 * @param structIndex
	 */
	private static void collectAssertContextVec(Struct struct, Map<Integer, Integer> contextVecMap,
			int structIndex) {
		if(structIndex <= INDEX_MEANING_THRESHOLD){
			return;
		}
		Struct parentStruct = struct.parentStruct();
		if(null != parentStruct){
			String parentStructType = parentStruct.type();
			if(parentStructType.equals("assert") && struct == parentStruct.prev1()){
				//e.g. "A covers B", A is the parent of cover, cover is parent of B
				String verb = getVerbStrFromAssert(parentStruct);
				if(!"".equals(verb)){
					setContextVecEntry(struct, verb, structIndex, contextVecMap);
				}
			}
		}
	}
	
	private static void collectVPContextVec(Struct struct, Map<Integer, Integer> contextVecMap,
			int structIndex) {
		if(structIndex <= INDEX_MEANING_THRESHOLD){
			return;
		}
		Struct parentStruct = struct.parentStruct();
		if(null != parentStruct){
			String parentStructType = parentStruct.type();
			if(parentStructType.equals("verbphrase") && struct == parentStruct.prev2()){
				//e.g. "A covers B", A is the parent of cover, cover is parent of B
				int verbIndex = getVerbIndexFromVP(parentStruct);
				if(verbIndex > 0){
					contextVecMap.put(structIndex, verbIndex);
				}
			}
		}
	}	
	
	private static int getVerbIndexFromVP(Struct vpStruct){
		
		if(vpStruct.prev1NodeType().isTypeStruct()){
			String verb = ((Struct)vpStruct.prev1()).nameStr();				
			WordMapIndexPair pair = WordForms.uniformizeWordAndGetIndex(verb, contextKeywordIndexDict);
			if(pair != WordMapIndexPair.placeholderWordMapIndexPair()){
				return pair.mapIndex();
			}
		}		
		return -1;
	}
	
	/**
	 * Retrieve verb used in an assertion struct.
	 * @param assertStruct Struct of type "assert"
	 * @return
	 */
	//not used currently
	private static String getVerbStrFromVP(Struct vpStruct){		
		if(vpStruct.prev1NodeType().isTypeStruct()){
			Struct verbStruct = ((Struct)vpStruct.prev1());
			return verbStruct.nameStr();
		}
		return "";
	}
	
	/**
	 * Retrieve verb used in an assertion struct.
	 * @param assertStruct Struct of type "assert"
	 * @return
	 */
	private static String getVerbStrFromAssert(Struct assertStruct){
		if(assertStruct.prev2NodeType().isTypeStruct()){
			Struct vpStruct = ((Struct)assertStruct.prev2());
			if(vpStruct.prev1NodeType().isTypeStruct()){
				Struct verbStruct = ((Struct)vpStruct.prev1());
				return verbStruct.nameStr();
			}	
		}	
		return "";
	}
	/**
	 * Adjusts contextVec based on wrapper.
	 * @param struct
	 * @param contextVec
	 */
	private static void adjustFromWrapper(Struct struct, Map<Integer, Integer> contextVecMap) {
		List<WLCommandWrapper> wrapperList = struct.WLCommandWrapperList();		
		if(wrapperList != null){
			WLCommandWrapper wrapper = wrapperList.get(0);
			//System.out.println("struct! " + struct + " |||wrapper! " + wrapper);
			ParseRelation parseRelation = ParseRelation.getParseRelation(struct, wrapper.WLCommandStr());
				//headVecEntry = parseRelation.relationNum;
				//System.out.println("HERE (in ParseTreeToVec)" + commandWrapper == null ? "" : commandWrapper.WLCommandStr());			
			ParseRelation.adjustContextVec(struct, contextVecMap, wrapper, parseRelation);			
		}
	}
	
	/**
	 * Sets the context vector entry according to the content of struct. I.e.
	 * if StructA whose prev1 is string, use that, if StructH, use name.
	 * Only set if word is relevant, i.e. in word map in ThmSearch.
	 * Should be used as *leaf* addition, i.e. end of recursion for either StructH
	 * or StructA.
	 * @param struct 
	 * @param contextVec 
	 * @param parentRowIndex the row index of the parent of struct, or the relationNum
	 * if headStruct, e.g. ParseRelation.EXISTS for Exists[structH]
	 * @return row index corresponding to Struct that was just inserted into.
	 */
	private static int setContextVecEntry(Struct struct, String termStr, int structParentIndex, Map<Integer, Integer> contextVecMap){
		//sets the entry to the index of the parent	
		
		//String termStr = struct.contentStr();
		if(WordForms.getWhiteEmptySpacePattern().matcher(termStr).matches()) return structParentIndex;
		
		//add each individual word.
		//to further differentiate the vector, and add useful words if applicable.
		//split properties by whitespace. E.g. "all maximal" -> "all", "maximal"
		String[] termStrAr = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(termStr); //termStr.split(" ");
		Integer lastWordRowIndex = null;
		int termStrArLen = termStrAr.length;
		
		if(termStrArLen > 1){
			String lastWord = termStrAr[termStrArLen-1];
			//lastWordRowIndex = contextKeywordIndexDict.get(lastWord);
			int parentIndex = structParentIndex;			
			WordMapIndexPair wordMapIndexPair = WordForms.uniformizeWordAndGetIndex(lastWord, contextKeywordIndexDict);			
			//WordMapIndexPair wordMapIndexPair = WordForms.WordMapIndexPair.placeholderWordMapIndexPair();
			if(WordForms.WordMapIndexPair.placeholderWordMapIndexPair() != wordMapIndexPair){
				lastWordRowIndex = wordMapIndexPair.mapIndex();
				parentIndex = lastWordRowIndex;
				addTermStrToVec(struct, wordMapIndexPair.word(), structParentIndex, contextVecMap);
			}			
			/*if(null != lastWordRowIndex){
				parentIndex = lastWordRowIndex;
				addTermStrToVec(struct, lastWord, structParentIndex, contextVecMap);
			}*/ //else do nothing, because lastWord does not have an entry in term-document matrix anyway
			
			//set the parent of the prior words as the index of the last word. But if word-pair
			//is adverb-adj, point adverb to adj.
			for(int i = 0; i < termStrArLen - 1; i++){
				String word = termStrAr[i];
				WordMapIndexPair curWordMapIndexPair = WordForms.uniformizeWordAndGetIndex(word, contextKeywordIndexDict);
				
				if(i > 0 && curWordMapIndexPair != WordMapIndexPair.placeholderWordMapIndexPair()
						//wordIndex != null
						){					
					Integer wordIndex = curWordMapIndexPair.mapIndex();					
					List<String> posList = posMMap.get(word);
					boolean isAdj = false;
					for(String pos : posList){
						if(pos.equals("adj")){
							isAdj = true;
						}
					}
					if(isAdj){
						String prevWord = termStrAr[i-1];
						int prevWordLen = prevWord.length();
						//e.g. "perfectly clear sentence", "perfectly" describes "clear", not "sentence"
						if((prevWordLen > 2 && prevWord.substring(prevWordLen-2, prevWordLen).equals("ly")) 
								|| posMMap.containsEntry(prevWord, "adverb")){
							//update parent index of prevWord.
							boolean forceAdjust = true;
							addTermStrToVec(struct, prevWord, wordIndex, contextVecMap, forceAdjust);
						}
					}
					word = curWordMapIndexPair.word();
				}
				addTermStrToVec(struct, word, parentIndex, contextVecMap);				
			}
		}		
		int termRowIndex = addTermStrToVec(struct, termStr, structParentIndex, contextVecMap);	
		//if termStr was not added, use lastWordRowIndex. Relies on fact that addTermStrToVec()
		//returns structParentIndex back if termStr not added.
		termRowIndex = (termRowIndex == structParentIndex && null != lastWordRowIndex) ? lastWordRowIndex : termRowIndex;		
		return termRowIndex;
	}

	/**
	 * Given a contentStr of a Struct, get the index most likely
	 * to correspond to the String.
	 * 
	 * @param termStr
	 * @return @Nullable likely index for termStr, could be null.
	 */
	/*public static Integer getTermStrIndex(String termStr){
		Integer rowIndex = WordForms.uniformizeWordAndGetIndex(termStr, contextKeywordIndexDict);
		if(null == rowIndex){
			String[] termStrAr = termStr.split(" ");
			int len = termStrAr.length;
			if(len > 1){
				rowIndex = contextKeywordIndexDict.get(termStrAr[len-1]);
			}
		}
		return rowIndex;
	}*/
	
	private static int addTermStrToVec(Struct struct, String termStr, int structParentIndex, Map<Integer, Integer> contextVecMap) {
		boolean forceAdjust = false;
		return addTermStrToVec(struct, termStr, structParentIndex, contextVecMap, forceAdjust);
	}
	
	/**
	 * Auxiliary method for setContextVecEntry.
	 * *Should* return structParentIndex if termStr not added to contextVec, else update
	 * setContextVecEntry().
	 * @param struct
	 * @param termStr
	 * @param structParentIndex
	 * @param contextVec
	 * @return
	 */
	private static int addTermStrToVec(Struct struct, String termStr, int structParentIndex, 
			Map<Integer, Integer> contextVecMap, boolean forceAdjust) {
		Integer termRowIndex = contextKeywordIndexDict.get(termStr);
		//to be used if no appropriate termRowIndex found
		Integer someRelatedWordIndex = null;
		if(termRowIndex == null){
			//de-singularize, and remove "-ed" and "ing"! But parsed Strings should already been singularized!
			termRowIndex = contextKeywordIndexDict.get(WordForms.getSingularForm(termStr));
		}
		List<String> relatedWordsList = null;
		//add the indices of related words as well.
		RelatedWords relatedWords;
		if(!GATHERING_DATA_BOOL){
			relatedWords = relatedWordsMap.get(termStr);
			if(null != relatedWords){
				relatedWordsList = relatedWords.getCombinedList();
			}
		}
		//System.out.println("##### Setting context vec, termStr " + termStr + " termRowIndex " + termRowIndex);
		if(termRowIndex == null){
			//removes endings such as -ing, and uses synonym rep.
			termStr = WordForms.normalizeWordForm(termStr);
			if(!GATHERING_DATA_BOOL){
			//check related words again
				if(null == relatedWordsList){
					relatedWords = relatedWordsMap.get(termStr);
					if(null != relatedWords){
						relatedWordsList = relatedWords.getCombinedList();
					}
				}
			}
			termRowIndex = contextKeywordIndexDict.get(termStr);
		}
		if(!GATHERING_DATA_BOOL){
			if(null != relatedWordsList){
				for(String relatedWord : relatedWordsList){
					//Related words themselves have already been normalized w.r.t. the dictionary.
					Integer relatedWordRowIndex = contextKeywordIndexDict.get(relatedWord);
					if(null != relatedWordRowIndex){
						if(null == someRelatedWordIndex){ 
							someRelatedWordIndex = relatedWordRowIndex;
						}
						Integer prevParentIndex = contextVecMap.get(relatedWordRowIndex);
						if(null == prevParentIndex || prevParentIndex < 0 || forceAdjust){ //contextVec[relatedWordRowIndex] <= 0
							contextVecMap.put(relatedWordRowIndex, structParentIndex);
							//contextVec[relatedWordRowIndex] = structParentIndex;
						}
					}
				}
			}
		}
		if(termRowIndex != null){
			//if hasn't been assigned to a valid index before, or only relational index
			Integer prevParentIndex = contextVecMap.get(termRowIndex);
			if(null == prevParentIndex || prevParentIndex < 0 || forceAdjust){//contextVec[termRowIndex] <= 0 
				contextVecMap.put(termRowIndex, structParentIndex);
				//contextVec[termRowIndex] = structParentIndex;
			}
			if(DEBUG) 
				if(true) System.out.println("ParseTreeToContextVec - termStr " + termStr + " ### rowIndex " + termRowIndex + " parent index " + structParentIndex);
		}else{
			//pass parentIndex down to children, in case of intermediate StructA that doesn't have a content string.
			//eg assert[A, B], the assert does not have content string.
			if(null != someRelatedWordIndex){
				termRowIndex = someRelatedWordIndex;
			}else{
				termRowIndex = structParentIndex;
			}			
		}
		return termRowIndex;
	}		
	
	/**
	 * Number indicating a parse relation, used to build context vector.
	 * e.g. "Exists[structH]" writes enum -2 at the index of structH 
	 *
	 */
	public enum ParseRelation{
		EXISTS(-8),
		HASPPT(-8),
		FORALL(-8),
		ELEMENT(-7),
		//default is 1, to differentiate between the words
				//used and the words that are not used
		DEFAULT(-10);
		
		private final int relationNum;
		
		ParseRelation(int relation){
			this.relationNum = relation;
		}
		
		public int relationNum(){
			return this.relationNum;
		}
		
		/**
		 * Given WLCommandStr, return the relationNum for that command.
		 * default is 0, which works as the deault context entry for
		 * a term is 0.
		 * @param WLCommandStr
		 * @param struct Struct from which structure of the command can be extracted.
		 * @return
		 */
		public static ParseRelation getParseRelation(Struct struct, String WLCommandStr){
			
			if(WLCommandStr.contains("\\[Element]")){
				//use the WLCommand structure to determine A and B in A\[Element]B,
				//set entry at B to the row index of A: B points to A.
				//setContextVec(struct, contextVec, commandWrapper, ELEMENT);
				return ELEMENT;
			}else if(WLCommandStr.contains("Exists")){
				return EXISTS;
			}else if(WLCommandStr.contains("HasProperty")){
				return HASPPT;
			}else if(WLCommandStr.contains("\\[ForAll]")){
				return FORALL;
			}else{
				return DEFAULT;
			}
		}
		
		public static int DEFAULT_RELATION_NUM(){
			return DEFAULT.relationNum;
		}
		
		/**
		 * Adjusts context vector based on the WLCommand. E.g. A \[Element] B results
		 * in B pointing to A.
		 * @param struct
		 * @param contextVec
		 * @param commandWrapper
		 * @return
		 */
		public static void adjustContextVec(Struct struct, Map<Integer, Integer> contextVecMap, WLCommandWrapper commandWrapper,
				ParseRelation parseRelation){
			//String WLCommandStr = commandWrapper.WLCommandStr();
			if(parseRelation.equals(ELEMENT)){
				//use the WLCommand structure to determine A and B in A\[Element]B,
				//set entry at B to the row index of A: B points to A.
				setContextVec(struct, contextVecMap, commandWrapper, ELEMENT);
				
			}else if(parseRelation.equals(HASPPT)){
				//use the WLCommand structure to determine A and B in A\[Element]B,
				//set entry at B to the row index of A: B points to A.
				setContextVec(struct, contextVecMap, commandWrapper, HASPPT);				
			}
			
		}
		
		/**
		 * Sets the context vec to take into account e.g. A\[Element] B.
		 */
		private static void setContextVec(Struct struct, Map<Integer, Integer> contextVecMap, WLCommandWrapper commandWrapper,
				ParseRelation relation){
			//get the list of PosTerms, containing info on the structs making up the command
			WLCommand command = commandWrapper.WLCommand();
			List<PosTerm> posTermList = WLCommand.posTermList(command);
			int triggerTermIndex = WLCommand.triggerWordIndex(command);
			//WLCommand tempCo  = struct.parentStruct().parentStruct().parentStruct().WLCommandWrapperList().get(0).WLCommand();
			//System.out.println("!!posTermList.get(0).posTermStruct() " + posTermList.get(0).posTermStruct() + posTermList.get(0).posTermStruct());
			if(DEBUG){
				System.out.println("posTermList " + posTermList + " triggerTermIndex " + triggerTermIndex);
			}
			switch(relation){
			case ELEMENT:
				adjustParentIndex(posTermList, command, triggerTermIndex, contextVecMap);
				break;
			case HASPPT:
				adjustParentIndex(posTermList, command, triggerTermIndex, contextVecMap);
				break;
			default:
				
			}				
		}
		
		/**
		 * Adjusts context vec entry for parent, to take into account triggered WLCommands 
		 * e.g. A \[Element] B, so that B points to A.
		 * @param posTermList
		 * @param command
		 * @param triggerTermIndex
		 * @param contextVec
		 */
		private static void adjustParentIndex(List<PosTerm> posTermList, WLCommand command, int triggerTermIndex, 
				Map<Integer, Integer> contextVecMap){
			/*for(int i = 0; i < posTermList.size(); i++){
			PosTerm posTerm = posTermList.get(i);
			System.out.println(" STRUCT " + posTermList.get(i).commandComponent());
			System.out.println(" STRUCTLIST " + WLCommand.getStructList(command, posTermList.get(i).commandComponent()));
			System.out.println(" CommandsMap " + WLCommand.getStructList(command, posTermList.get(i).commandComponent()).get(posTerm.positionInMap()));
			}*/
		//elements are of form A\[Element] B, get A and then B. 
		Struct parentStruct = null;
		for(int i = triggerTermIndex - 1; i > -1; i--){
			
			PosTerm posTerm = posTermList.get(i);
			int positionInCommandMap = posTerm.positionInMap();
			//e.g. -2 indicates it's an AUX/auxiliary term
			if(positionInCommandMap < 0 || posTerm.isNegativeTerm()){
				continue;
			}
			
			Struct curStruct = posTerm.posTermStruct();
			//System.out.println("curStruct in p.t.t.v" + curStruct);
			//not a better way around this. <--this way below leads to infinite recursion.
			//curStruct = WLCommand.getStructList(command, posTerm.commandComponent()).get(positionInCommandMap);
			
			//System.out.println("***TPYE " + curStruct);
			if(curStruct != null && curStruct.type().matches("ent|symb|pro")){
				//set entry of parent in contextVec to itself, if existing entry is <=0, 
				//so not to differentiate the term too much from likely counterpart in corpus
				//vectors. Experimentation.
				//disable this since setting all irrelevant thm terms to 0
				/*String termStr = curStruct.contentStr();						
				Integer parentIndex = getTermStrIndex(termStr);
				
				if(parentIndex != null && contextVec[parentIndex] <= 0){
					contextVec[parentIndex] = parentIndex;
				} */
				
				parentStruct = curStruct;					
				break;
			}
		}
		
		if(null == parentStruct){
			//System.out.println("Parent struct not found! triggerIndex " + triggerTermIndex);
			return;
		}
		
		//get content string for parentStruct
		String parentTermStr = parentStruct.nameStr();
		
		//get an index for contentStr, should already been singularized				
		WordMapIndexPair parentTermRowPair = WordForms.uniformizeWordAndGetIndex(parentTermStr, contextKeywordIndexDict);
		if(WordMapIndexPair.placeholderWordMapIndexPair() == parentTermRowPair){
			return;
		}
		int parentTermRowIndex = parentTermRowPair.mapIndex();
		//parentTermRowIndex = parentTermRowIndex == null ? ELEMENT.relationNum : parentTermRowIndex;
		//System.out.println("***** *got to adjust.");
		/*This has sometimes resulted in infinite loops! When the Struct in an earlier PosTerm is a child  
		 of the Struct of a later PosTerm, due to erroneous non-linear Struct-adding to posTermList of the command. */
		for(int i = triggerTermIndex+1; i < posTermList.size(); i++){
			Struct curStruct = posTermList.get(i).posTermStruct();
			if(null != curStruct){
				//System.out.println("!another term with index: " + i);
				//String curStructTermStr = curStruct.contentStr();
				//addTermStrToVec(curStruct, curStructTermStr, parentTermRowIndex, contextVec);
				//System.out.println("!curStruct " + curStruct);
				boolean adjustVecFromCommand = false;
				curStruct.setContextVecEntry(parentTermRowIndex, contextVecMap, adjustVecFromCommand);
			}
		}
		}
	}
}
