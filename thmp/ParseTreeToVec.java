package thmp;

import java.util.List;
import java.util.Map;

import thmp.Struct.NodeType;
import thmp.search.TriggerMathThm2;

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
	 * @param WLCommandStr
	 * @return
	 */
	public static int[] tree2vec(Struct headStruct, int[] contextVec, String WLCommandStr){
		// curCommand's type determines the num attached to head of structH,
		// e.g. "Exists[structH]" writes enum ParseRelation -2 at the index of structH 
		//get the entry for head and sets it corresponding to the term for headStruct
		int headVecEntry = ParseRelation.getParseRelation(WLCommandStr);
		
		//set the contextVec entry of the headStruct
		int termRowIndex = setContextVecEntry(headStruct, headVecEntry, contextVec);
		
		return tree2vec(headStruct, termRowIndex, contextVec);
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
		
		//add struct itself, then its children
		int structIndex = setContextVecEntry(struct, structParentIndex, contextVec);
		
		List<Struct> children = struct.children();
		
		for(Struct child : children){
			//recurse if StructH, else call setContextVecEntry
			if(!child.isStructA()){
				tree2vec(child, structIndex, contextVec);
			}else{
				setContextVecEntry(child, structIndex, contextVec);
			}
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
		
		//add struct itself, then its prev1 and prev2
		int structIndex = setContextVecEntry(struct, structParentIndex, contextVec);
		
		//do prev1 and prev2
		if(struct.prev1NodeType().equals(NodeType.STRUCTA) || struct.prev1NodeType().equals(NodeType.STRUCTH)){
			tree2vec((Struct)struct.prev1(), structIndex, contextVec);
		}
		
		if(struct.prev2NodeType().equals(NodeType.STRUCTA) || struct.prev1NodeType().equals(NodeType.STRUCTH)){
			tree2vec((Struct)struct.prev2(), structIndex, contextVec);
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
	private static int setContextVecEntry(Struct struct, int structParentIndex, int[] contextVec){
		//sets the parent of the 
		
		String termStr = struct.contentStr();
		Integer termRowIndex = keywordDict.get(termStr);
		if(termRowIndex != null){
			//
			contextVec[termRowIndex] = structParentIndex;
		}
		return termRowIndex;
	}
	
	public static void main(String[] args){
		
	}
	
	/**
	 * Number indicating a parse relation, used to build context vector.
	 * e.g. "Exists[structH]" writes enum -2 at the index of structH 
	 *
	 */
	public enum ParseRelation{
		ELEMENT(-1),
		EXISTS(-2);
		
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
		 * @return
		 */
		public static int getParseRelation(String WLCommandStr){
			if(WLCommandStr.contains("\\[Element]")){
				return ELEMENT.relationNum;
			}else if(WLCommandStr.contains("Exists")){
				return EXISTS.relationNum;
			}else{
				return 0;
			}
		}
	}
}
