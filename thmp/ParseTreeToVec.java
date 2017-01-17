package thmp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ListMultimap;

import thmp.ParseToWLTree.WLCommandWrapper;
import thmp.Struct.NodeType;
import thmp.WLCommand.PosTerm;
import thmp.search.CollectThm;
import thmp.search.TriggerMathThm2;
import thmp.utils.WordForms;

/**
 * Converts a parse tree produced by ThmP1 
 * into a context vector, where the rows correspond 
 * to terms of the term-document matrix.
 * 
 * @author yihed
 */
public class ParseTreeToVec {

	//private static final Map<String, Integer> contextKeywordDict = TriggerMathThm2.allThmsKeywordIndexDict();
	//should ideally order the words according to relative distances apart, i.e. entries in the correlation matrix.
	//used for forming query vecs, as these are words used when the thm source vecs were formed.
	private static final Map<String, Integer> contextKeywordQueryDict = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_MAP();
	//contextVecWordsNextTimeMMap
	private static final Map<String, Integer> contextKeywordThmsDataDict = CollectThm.ThmWordsMaps.get_contextVecWordsNextTimeMap();
	//the current map to use, this *must* be set to contextKeywordThmsDataDict when producing vecs from thm data source. 
	//e.g. in DetectHypothesis.java. 
	private static Map<String, Integer> contextKeywordDict = contextKeywordQueryDict;
	//private static final Map<String, Integer> contextKeywordDict = ;
	private static final ListMultimap<String, String> posMMap = Maps.posMMap();
	
	/**
	 * Sets the dictionary to the mode for producing context vecs from data source to be searched.
	 * e.g. in DetectHypothesis.java.
	 */
	public static void set_contextKeywordDictToDataMode(){
		contextKeywordDict = contextKeywordThmsDataDict;
	}
	
	/**
	 * Entry method from ParseToWLTree, sets the term corresponding to head using WLCommandStr. 
	 * calls tree2vec.
	 * @param headStruct
	 * @param contextVec
	 * @return
	 */
	public static int[] tree2vec(Struct headStruct, int[] contextVec){
		return tree2vec(headStruct, contextVec, null);
	}
	
	/**
	 * Entry method from ParseToWLTree, sets the term corresponding to head using WLCommandStr. 
	 * calls tree2vec.
	 * @param headStruct
	 * @param contextVec
	 * @param WLCommandStr
	 * @return contextVec filled with indices.
	 */
	public static int[] tree2vec(Struct headStruct, int[] contextVec, WLCommandWrapper commandWrapper){
		// curCommand's type determines the num attached to head of structH,
		// e.g. "Exists[structH]" writes enum ParseRelation -2 at the index of structH 
		//get the entry for head and sets it corresponding to the term for headStruct
		ParseRelation parseRelation = null;
		int headVecEntry;
		if(commandWrapper != null){			
			parseRelation = ParseRelation.getParseRelation(headStruct, contextVec, commandWrapper.WLCommandStr());
			headVecEntry = parseRelation.relationNum;
			System.out.println("HERE (in ParseTreeToVec)" + commandWrapper == null ? "" : commandWrapper.WLCommandStr());
		}else{
			headVecEntry = ParseRelation.DEFAULT_RELATION_NUM();
			System.out.println("HERE (in ParseTreeToVec)");
		}
		//System.out.println("parseRelation headVecEntry " + headVecEntry);
		//System.out.println("headStruct " + headStruct);
		parseRelation = parseRelation == null ? ParseRelation.DEFAULT : parseRelation;
		
		//set the contextVec entry of the headStruct, returns the index of the entry being set
		//should set it for both children!
		//String termStr = headStruct.contentStr();
		//int termRowIndex = setContextVecEntry(headStruct, termStr, headVecEntry, contextVec);
		
		contextVec = tree2vec(headStruct, headVecEntry, contextVec);
		//adjust the context vector, to account for e.g. A \[Element] B
		if(commandWrapper != null){
			ParseRelation.adjustContextVec(headStruct, contextVec, commandWrapper, parseRelation);
		}
		return contextVec;
	}
	
	/**
	 * Convert tree to vector. Intermediate method, uses dynamic dispatch to call setContextVecEntry()
	 * on either StructA or StructH, which in turn calls setStructHContextVecEntry().
	 * @param headStruct head Struct to which current WLCommandStr is attached to.
	 * @param headStructRowIndex is index of parent (of headStruct).
	 * @param contextVec current context vector containing indices for terms in parsed string.
	 */
	private static int[] tree2vec(Struct headStruct, int headStructParentIndex, int[] contextVec){
		
		//sets the entry correponding to headStruct
		//setContextVecEntry(headStruct, contextVec, headStructRowIndex);
		//call the applicable method in either StructA or StructH, with dynamic dispatch
		headStruct.setContextVecEntry(headStructParentIndex, contextVec);
		return contextVec;
	}

	/**
	 * Double dispatch, called from StructH, to set the context vector. 
	 * @param struct
	 * @param contextVec
	 * @param structParentIndex row index of parent of struct
	 * @return
	 */
	public static void setStructHContextVecEntry(StructH<?> struct, int structParentIndex, int[] contextVec){
		//termsStr only contains name of struct, properties are considered below 
		String termStr = struct.nameStr();
		int structIndex = 0;
		//add struct itself, then its children
		//don't count parent is parent just all latex expressions
		if(!termStr.matches("\\$[^$]+\\$")){
			structIndex = setContextVecEntry(struct, termStr, structParentIndex, contextVec);
		}
		//if struct does not have entry, try again with
		
		//System.out.println("***inside StructH context vec setting, struct " + struct + " parentIndex " + structParentIndex);
		List<Struct> children = struct.children();
		
		for(Struct child : children){
			//System.out.println("child! " + child + " ||| parent! " + struct);
			tree2vec(child, structIndex, contextVec);
		}
		
		//should also set indices of ppt's of StructH, e.g. "maximal ideal", maximal is ppt of ideal 
		Set<String> propertySet = struct.getPropertySet();
		//mark indices for words that are properties
		for(String propertyStr : propertySet){		
			setContextVecEntry(struct, propertyStr, structIndex, contextVec);
			//separate here instead of in setContextVecEntry, make all  point to the ent			
		}
		
		adjustFromWrapper(struct, contextVec);
	}
	
	/**
	 * Double dispatch, called from StructH, to set the context vector. 
	 * @param struct
	 * @param contextVec
	 * @param structParentIndex row index of parent of struct
	 * @return
	 */
	public static void setStructAContextVecEntry(StructA<?, ?> struct, int structParentIndex, int[] contextVec){
		
		List<String> termStrList = struct.contentStrList();
		//be more specific, "algebraic closure", want "algebraic" to point to "closure", so "closure" is parent
		//of "algebraic". Consistent with "closure is algebraic" => A \[Elememt] B, B describes A, so points to A.

		//add the Strings fro struct itself, then its prev1 and prev2
		int structIndex = structParentIndex;
		int termStrListLen = termStrList.size();
		if(termStrListLen > 0){
			structIndex = setContextVecEntry(struct, termStrList.get(0), structParentIndex, contextVec);
		}
		for(int i = 1; i < termStrListLen; i++){
			setContextVecEntry(struct, termStrList.get(i), structParentIndex, contextVec);
		}
		//do prev1 and prev2
		if(struct.prev1NodeType().isTypeStruct()){
			tree2vec((Struct)(struct.prev1()), structIndex, contextVec);
		}
		
		if(struct.prev2NodeType().isTypeStruct()){
			tree2vec((Struct)(struct.prev2()), structIndex, contextVec);
		}		
		
		adjustFromWrapper(struct, contextVec);
	}

	/**
	 * Adjusts contextVec based on wrapper.
	 * @param struct
	 * @param contextVec
	 */
	private static void adjustFromWrapper(Struct struct, int[] contextVec) {
		List<WLCommandWrapper> wrapperList = struct.WLCommandWrapperList();		
		if(wrapperList != null){
			WLCommandWrapper wrapper = wrapperList.get(0);
			//System.out.println("struct! " + struct + " |||wrapper! " + wrapper);
			ParseRelation parseRelation = ParseRelation.getParseRelation(struct, contextVec, wrapper.WLCommandStr());
				//headVecEntry = parseRelation.relationNum;
				//System.out.println("HERE (in ParseTreeToVec)" + commandWrapper == null ? "" : commandWrapper.WLCommandStr());
			
			ParseRelation.adjustContextVec(struct, contextVec, wrapper, parseRelation);
			
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
	private static int setContextVecEntry(Struct struct, String termStr, int structParentIndex, int[] contextVec){
		//sets the entry to the index of the parent	
		
		//String termStr = struct.contentStr();
		if(termStr.matches("\\s*")) return structParentIndex;
		
		//add each individual word.
		//to further differentiate the vector, and add useful words if applicable.
		//split properties by whitespace. E.g. "all maximal" -> "all", "maximal"
		String[] termStrAr = termStr.split(" ");
		Integer lastWordRowIndex = null;
		int termStrArLen = termStrAr.length;
		
		if(termStrArLen > 1){
			String lastWord = termStrAr[termStrArLen-1];
			lastWordRowIndex = contextKeywordDict.get(lastWord);
			int parentIndex = structParentIndex;
			if(null != lastWordRowIndex){
				parentIndex = lastWordRowIndex;
				addTermStrToVec(struct, lastWord, structParentIndex, contextVec);
			}//else do nothing, because lastWord does not have an entry in term-document matrix anyway
			
			//set the parent of the prior words as the index of the last word. But if word-pair
			//is adverb-adj, point adverb to adj.
			for(int i = 0; i < termStrArLen - 1; i++){
				String word = termStrAr[i];
				Integer wordIndex = contextKeywordDict.get(word);
				if(i > 0 && wordIndex != null){
					List<String> posList = posMMap.get(word);
					boolean isAdj = false;
					for(String pos : posList){
						if(pos.equals("adj")){
							isAdj = true;
						}
					}
					if(isAdj){
						String prevWord = termStrAr[i-1];
						if(prevWord.matches(".*ly") || posMMap.containsEntry(prevWord, "adverb")){
							//modify parent index of prevWord.
							boolean forceAdjust = true;
							addTermStrToVec(struct, prevWord, wordIndex, contextVec, forceAdjust);
						}
					}
				}
				addTermStrToVec(struct, word, parentIndex, contextVec);
			}
		}
		
		int termRowIndex = addTermStrToVec(struct, termStr, structParentIndex, contextVec);	
		//if termStr was not added, use lastWordRowIndex. Relies on fact that addTermStrToVec()
		//returns structParentIndex back if termStr not added.
		termRowIndex = (termRowIndex == structParentIndex && null != lastWordRowIndex) ? lastWordRowIndex : termRowIndex;
		
		return termRowIndex;
	}

	/**
	 * Given a contentStr of a Struct, get the index most likely
	 * to correspond to the String.
	 * @param termStr
	 * @return @Nullable likely index for termStr, could be null.
	 */
	public static Integer getTermStrIndex(String termStr){
		Integer rowIndex = contextKeywordDict.get(termStr);
		if(null == rowIndex){
			String[] termStrAr = termStr.split(" ");
			int len = termStrAr.length;
			if(len > 1){
				rowIndex = contextKeywordDict.get(termStrAr[len-1]);
			}
		}
		return rowIndex;
	}
	
	private static int addTermStrToVec(Struct struct, String termStr, int structParentIndex, int[] contextVec) {
		boolean forceAdjust = false;
		return addTermStrToVec(struct, termStr, structParentIndex, contextVec, forceAdjust);
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
	private static int addTermStrToVec(Struct struct, String termStr, int structParentIndex, int[] contextVec, 
			boolean forceAdjust) {
		Integer termRowIndex = contextKeywordDict.get(termStr);
		if(termRowIndex == null){
			//de-singularize, and remove "-ed" and "ing"! But parsed Strings should already been singularized!
			termRowIndex = contextKeywordDict.get(WordForms.getSingularForm(termStr));
		}
		//System.out.println("##### Setting context vec, termStr " + termStr + " termRowIndex " + termRowIndex);
		if(termRowIndex != null){	
			//if hasn't been assigned to a valid index before, or only relational index
			if(contextVec[termRowIndex] <= 0 || forceAdjust){
				contextVec[termRowIndex] = structParentIndex;
			}
			System.out.println("struct " + struct + " termStr " + termStr + " ### rowIndex " + termRowIndex + " parent index " + structParentIndex);
		}else{
			//pass parentIndex down to children, in case of intermediate StructA that doesn't have a content string.
			//eg assert[A, B], the assert does not have content string.
			termRowIndex = structParentIndex;
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
		public static ParseRelation getParseRelation(Struct struct, int[] contextVec, String WLCommandStr){
			
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
		public static void adjustContextVec(Struct struct, int[] contextVec, WLCommandWrapper commandWrapper,
				ParseRelation parseRelation){
			//String WLCommandStr = commandWrapper.WLCommandStr();
			if(parseRelation.equals(ELEMENT)){
				//use the WLCommand structure to determine A and B in A\[Element]B,
				//set entry at B to the row index of A: B points to A.
				setContextVec(struct, contextVec, commandWrapper, ELEMENT);
				
			}else if(parseRelation.equals(HASPPT)){
				//use the WLCommand structure to determine A and B in A\[Element]B,
				//set entry at B to the row index of A: B points to A.
				setContextVec(struct, contextVec, commandWrapper, HASPPT);				
			}
			
		}
		
		/**
		 * Sets the context vec to take into account e.g. A\[Element] B.
		 */
		private static void setContextVec(Struct struct, int[] contextVec, WLCommandWrapper commandWrapper,
				ParseRelation relation){
			//get the list of PosTerms, containing info on the structs making up the command
			WLCommand command = commandWrapper.WLCommand();
			List<PosTerm> posTermList = WLCommand.posTermList(command);
			int triggerTermIndex = WLCommand.triggerWordIndex(command);
			//WLCommand tempCo  = struct.parentStruct().parentStruct().parentStruct().WLCommandWrapperList().get(0).WLCommand();
			//System.out.println("!!posTermList.get(0).posTermStruct() " + posTermList.get(0).posTermStruct() + posTermList.get(0).posTermStruct());
			//WLCommand.posTermList(tempCo).get(0).posTermStruct()
			System.out.println("posTermList " + posTermList + " triggerTermIndex " + triggerTermIndex);
			switch(relation){
			case ELEMENT:
				adjustParentIndex(posTermList, command, triggerTermIndex, contextVec);
				break;
			case HASPPT:
				adjustParentIndex(posTermList, command, triggerTermIndex, contextVec);
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
		private static void adjustParentIndex(List<PosTerm> posTermList, WLCommand command, int triggerTermIndex, int[] contextVec){
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
		Integer parentTermRowIndex = getTermStrIndex(parentTermStr);
		if(null == parentTermRowIndex){
			return;
		}
		//parentTermRowIndex = parentTermRowIndex == null ? ELEMENT.relationNum : parentTermRowIndex;
		System.out.println("***** *got to adjust.");
		//This has sometimes resulted in infinite loops, when the Struct in an earlier PosTerm is a child  
		//of the Struct of a later PosTerm, due to erroneous non-linear Struct-adding to posTermList of the command. 
		for(int i = triggerTermIndex+1; i < posTermList.size(); i++){
			Struct curStruct = posTermList.get(i).posTermStruct();
			if(null != curStruct){
				System.out.println("!another term with index: " + i);
				//String curStructTermStr = curStruct.contentStr();
				//addTermStrToVec(curStruct, curStructTermStr, parentTermRowIndex, contextVec);
				System.out.println("!curStruct " + curStruct);
				curStruct.setContextVecEntry(parentTermRowIndex, contextVec);
			}
		}
		}
	}
}
