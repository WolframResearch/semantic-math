package thmp;

import java.util.List;
import java.util.Map;
import java.util.Set;

import thmp.ParseToWLTree.WLCommandWrapper;
import thmp.Struct.NodeType;
import thmp.WLCommand.PosTerm;
import thmp.search.TriggerMathThm2;
import thmp.utils.WordForms;

/**
 * Converts a parse tree produced by ThmP1 
 * into a vector, where the rows correspond 
 * to terms of the term-document matrix.
 * 
 * @author yihed
 */
public class ParseTreeToVec {

	private static final Map<String, Integer> keywordDict = TriggerMathThm2.keywordDict();
	
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
			System.out.println("HERE" + commandWrapper == null ? "" : commandWrapper.WLCommandStr());
		}else{
			headVecEntry = ParseRelation.DEFAULT_RELATION_NUM();
			System.out.println("HERE");
		}
		//System.out.println("parseRelation headVecEntry " + headVecEntry);
		//System.out.println("headStruct " + headStruct);
		parseRelation = parseRelation == null ? ParseRelation.DEFAULT : parseRelation;
		
		//set the contextVec entry of the headStruct, returns the index of the entry being set
		//should set it for both children!
		//String termStr = headStruct.contentStr();
		//int termRowIndex = setContextVecEntry(headStruct, termStr, headVecEntry, contextVec);
		//System.out.println("termRowIndex " + termRowIndex);
		
		contextVec = tree2vec(headStruct, headVecEntry, contextVec);
		//adjust the context vector, to account for e.g. A \[Element] B
		if(commandWrapper != null){
			ParseRelation.adjustContextVec(headStruct, contextVec, commandWrapper, parseRelation);
		}
		return contextVec;
		//return tree2vec(headStruct, termRowIndex, contextVec);
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
		
		String termStr = struct.contentStr();
		//add struct itself, then its children
		int structIndex = setContextVecEntry(struct, termStr, structParentIndex, contextVec);
		//if struct does not have entry, try again with
		
		//System.out.println("***inside StructH context vec setting, struct " + struct + " parentIndex " + structParentIndex);
		List<Struct> children = struct.children();
		
		for(Struct child : children){
			tree2vec(child, structIndex, contextVec);			
		}
		
		//should also set indices of ppt's of StructH, e.g. "maximal ideal", maximal is ppt of ideal 
		Set<String> propertySet = struct.getPropertySet();
		//mark indices for words that are properties
		for(String propertyStr : propertySet){		
			setContextVecEntry(struct, propertyStr, structIndex, contextVec);
			//separate here instead of in setContextVecEntry, make all  point to the ent
			
		}
	}
	
	/**
	 * Double dispatch, called from StructH, to set the context vector. 
	 * @param struct
	 * @param contextVec
	 * @param structParentIndex row index of parent of struct
	 * @return
	 */
	public static void setStructAContextVecEntry(StructA<?, ?> struct, int structParentIndex, int[] contextVec){
		
		String termStr = struct.contentStr();
		//be more specific, "algebraic closure", want "algebraic" to point to "closure", so "closure" is parent
		//of "algebraic". Consistent with "closure is algebraic" => A \[Elememt] B, B describes A, so points to A.
		
		
		//add struct itself, then its prev1 and prev2
		int structIndex = setContextVecEntry(struct, termStr, structParentIndex, contextVec);
		//do prev1 and prev2
		if(struct.prev1NodeType().equals(NodeType.STRUCTA) || struct.prev1NodeType().equals(NodeType.STRUCTH)){
			tree2vec((Struct)(struct.prev1()), structIndex, contextVec);
		}
		
		if(struct.prev2NodeType().equals(NodeType.STRUCTA) || struct.prev2NodeType().equals(NodeType.STRUCTH)){
			tree2vec((Struct)(struct.prev2()), structIndex, contextVec);
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
			lastWordRowIndex = keywordDict.get(lastWord);
			int parentIndex = structParentIndex;
			if(null != lastWordRowIndex){
				parentIndex = lastWordRowIndex;
				addTermStrToVec(struct, lastWord, structParentIndex, contextVec);
			}//else do nothing, because lastWord does not have an entry in term-document matrix anyway
			
			for(int i = 0; i < termStrArLen - 1; i++){
				String word = termStrAr[i];
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
	 */
	private static Integer getTermStrIndex(String termStr){
		Integer rowIndex = keywordDict.get(termStr);
		if(null == rowIndex){
			String[] termStrAr = termStr.split(" ");
			int len = termStrAr.length;
			if(len > 1){
				rowIndex = keywordDict.get(termStrAr[len-1]);
			}
		}
		return rowIndex;
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
	private static int addTermStrToVec(Struct struct, String termStr, int structParentIndex, int[] contextVec) {
		Integer termRowIndex = keywordDict.get(termStr);
		if(termRowIndex == null){
			//de-singularize, and remove "-ed" and "ing"! But parsed Strings should already been singularized!
			termRowIndex = keywordDict.get(WordForms.getSingularForm(termStr));
		}
		//System.out.println("##### Setting context vec, termStr " + termStr + " termRowIndex " + termRowIndex);
		if(termRowIndex != null){	
			//if hasn't been assigned to a valid index before, or only relational index
			if(contextVec[termRowIndex] <= 0){
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
			System.out.println("posTermList " + posTermList + " triggerTermIndex " + triggerTermIndex);
			switch(relation){
			case ELEMENT:
				/*for(int i = 0; i < posTermList.size(); i++){
					PosTerm posTerm = posTermList.get(i);
					System.out.println(" STRUCT " + posTermList.get(i).commandComponent());
					System.out.println(" STRUCTLIST " + WLCommand.getStructList(command, posTermList.get(i).commandComponent()));
					System.out.println(" CommandsMap " + WLCommand.getStructList(command, posTermList.get(i).commandComponent()).get(posTerm.positionInMap()));
				}*/
				//elements are of form A\[Element] B, get A and then B. 
				Struct parentStruct = null;
				for(int i = 0; i < triggerTermIndex; i++){
					if(i == triggerTermIndex){
						continue;
					}
					PosTerm posTerm = posTermList.get(i);
					Struct curStruct = posTerm.posTermStruct();
					//obfuscated!
					curStruct = WLCommand.getStructList(command, posTerm.commandComponent()).get(posTerm.positionInMap());
					System.out.println("***TPYE " + curStruct);
					if(curStruct != null && curStruct.type().matches("ent|symb|pro")){
						
						parentStruct = curStruct;						
						break;
					}
				}
				
				if(null == parentStruct){
					return;
				}
				
				//get termStr for parentStruct
				String parentTermStr = parentStruct.contentStr();
				//get an index for contentStr, should already been singularized
				
				Integer parentTermRowIndex = getTermStrIndex(parentTermStr);
				if(null == parentTermRowIndex){
					return;
				}
				//parentTermRowIndex = parentTermRowIndex == null ? ELEMENT.relationNum : parentTermRowIndex;
				System.out.println("***** *got to adjust" );
				for(int i = triggerTermIndex+1; i < posTermList.size(); i++){
					Struct curStruct = posTermList.get(i).posTermStruct();
					if(null != curStruct){
						
						//String curStructTermStr = curStruct.contentStr();
						//addTermStrToVec(curStruct, curStructTermStr, parentTermRowIndex, contextVec);
						curStruct.setContextVecEntry(parentTermRowIndex, contextVec);
					}
				}
				break;
			default:
				
			}
				
		}
		
		
	}
}
