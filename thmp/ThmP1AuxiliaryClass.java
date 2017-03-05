package thmp;

import java.util.ArrayList;
import java.util.List;

import thmp.Struct.NodeType;

public class ThmP1AuxiliaryClass {

	public static class ConjDisjVerbphrase{
		private boolean hasConjDisjVerbphrase;
		//"assert" type found. Change this assert to conj_ or disj_assert,
		//rather than using conj_ or disj_verbphrase 
		private boolean assertTypeFound;
		private Struct assertStruct;
		private ConjDisjType conjDisjType;
		
		public static enum ConjDisjType{
			CONJ, DISJ;
			/**
			 * Get the ConjDisjType from the type. structType should have been
			 * checked to have either CONJ or DISJ type.
			 * @param type
			 * @return
			 */
			public static ConjDisjType findConjDisjType(String structType){
				return structType.charAt(0) == 'c' ?
						ConjDisjType.CONJ : ConjDisjType.DISJ;
			}
		}
		
		public ConjDisjVerbphrase() {
		}
		
		public ConjDisjVerbphrase(boolean hasConjDisjVerbphrase, boolean isRightChild,
				ConjDisjType conjDisjType) {
			this.hasConjDisjVerbphrase = hasConjDisjVerbphrase;
			this.assertTypeFound = isRightChild;
			this.conjDisjType = conjDisjType;
		}
		
		/**
		 * @return the assertStruct
		 */
		public Struct getAssertStruct() {
			return assertStruct;
		}

		/**
		 * @param assertStruct the assertStruct to set
		 */
		public void setAssertStruct(Struct assertStruct) {
			this.assertStruct = assertStruct;
		}

		/**
		 * @return the conjDisjType
		 */
		public ConjDisjType getConjDisjType() {
			return conjDisjType;
		}

		/**
		 * @param conjDisjType the conjDisjType to set
		 */
		public void setConjDisjType(ConjDisjType conjDisjType) {
			this.conjDisjType = conjDisjType;
		}

		/**
		 * @param hasConjDisjVerbphrase the hasConjDisjVerbphrase to set
		 */
		public void setHasConjDisjVerbphrase(boolean hasConjDisjVerbphrase) {
			this.hasConjDisjVerbphrase = hasConjDisjVerbphrase;
		}
		
		/**
		 * @param assertTypeFound the isRightChild to set
		 */
		public void setAssertParentFound(boolean assertTypeFound) {
			this.assertTypeFound = assertTypeFound;
		}
		
		/**
		 * @return the hasConjDisjVerbphrase
		 */
		public boolean isHasConjDisjVerbphrase() {
			return hasConjDisjVerbphrase;
		}
		
		/**
		 * @return the isRightChild
		 */
		public boolean assertTypeFound() {
			return assertTypeFound;
		}
		
		/**
		 * Reorganize tree to take into account conj or disj of verbphrases,
		 * to facilitate grammar rule pick up later.
		 * @param uHeadStruct
		 * @return
		 */
		public static Struct reorganizeConjDisjVerbphraseTree(
				ConjDisjVerbphrase conjDisjVerbphrase) {
			
			Struct uHeadStruct = conjDisjVerbphrase.assertStruct;
			//StructH not valid head of sentence, since that'd represent one entity.
			if(!uHeadStruct.isStructA()){
				return uHeadStruct;
			}
			//parent needs to be taken here instead of later, since the new StructA construction
			//can change the parent to the newly constructed StructA.
			Struct structParent = uHeadStruct.parentStruct();
			//should find the nearest assert instead of going all the way to the head!
			Struct uHeadStructCopy = uHeadStruct.copy();
			
			reorganizeConjDisjVerbphraseTree(uHeadStruct, uHeadStructCopy);
			
			String newType = conjDisjVerbphrase.conjDisjType == ConjDisjType.CONJ ?
					"conj_" : "disj_";
			Struct newHead = new StructA<Struct, Struct>(uHeadStruct, NodeType.STRUCTA, 
					uHeadStructCopy, NodeType.STRUCTA, 
					newType + uHeadStruct.type());
			
			uHeadStruct.set_parentStruct(newHead);
			uHeadStructCopy.set_parentStruct(newHead);
			
			//set parent struct			
			if(null != structParent){
				newHead.set_parentStruct(structParent);
				if(uHeadStruct == structParent.prev2()){
					structParent.set_prev2(newHead);
				}else{
					structParent.set_prev1(newHead);
				}
				//throw new IllegalStateException("\n"+newHead.toString());
			}
			return newHead;
		}
		
		/**
		 * 
		 * @param struct
		 * @param secondTreeStruct
		 */
		private static void reorganizeConjDisjVerbphraseTree(Struct struct, Struct secondTreeStruct) {

			Struct structParent = struct.parentStruct();
			if(struct.type().equals("conj_verbphrase") || struct.type().equals("disj_verbphrase")){
				//because conj_verbphrase should be right child as previously checked, check with assert.
				secondTreeStruct.parentStruct().set_prev2(struct.prev2());
				structParent.set_prev2(struct.prev1());
				return;
			}
			
			//this assumes that StructH doesn't have conj_verbphrases has children.
			//i.e. no such grammar rule exists. Figure out better way or do more checking!
			//adjust scores!
			if(struct.prev1NodeType().isTypeStruct()){
				if(struct.prev1NodeType().equals(NodeType.STRUCTH)){
					Struct newLeftChild = ((Struct)struct.prev1()).copy();
					newLeftChild.set_parentStruct(secondTreeStruct);
					secondTreeStruct.set_prev1(newLeftChild);
				}
				else{
					Struct prev1Struct = (Struct)struct.prev1();
					
					//this is shallow copy, i.e. don't copy content of prev1() and prev2().
					Struct newLeftChild = prev1Struct.copy();					
					newLeftChild.set_parentStruct(secondTreeStruct);
					secondTreeStruct.set_prev1(newLeftChild);
					reorganizeConjDisjVerbphraseTree((Struct)struct.prev1(), newLeftChild);
				}
			}//type string has already been copied in the previous level's copy()
			
			if(struct.prev2NodeType().isTypeStruct()){
				if(struct.prev2NodeType().equals(NodeType.STRUCTH)){
					Struct newLeftChild = ((Struct)struct.prev2()).copy();
					newLeftChild.set_parentStruct(secondTreeStruct);
					secondTreeStruct.set_prev2(newLeftChild);
				}
				else{
					//prev1 cannot be conj_verbphrase at this point! do assert
					
					//this is shallow copy
					Struct newLeftChild = ((Struct)struct.prev2()).copy();
					newLeftChild.set_parentStruct(secondTreeStruct);
					secondTreeStruct.set_prev2(newLeftChild);
					reorganizeConjDisjVerbphraseTree((Struct)struct.prev2(), newLeftChild);
				}
			}			
		}
	}	

	/**
	 * @param structList
	 */
	protected static void convertStructToTexAssert(List<Struct> structList) {
		int structListSz  = structList.size();
		if(0 == structListSz) return;
		
		Struct lastStruct = structList.get(structListSz-1);
		Struct firstStruct = structList.get(0);		
		if(structListSz > 3){			
			firstStruct = structList.get(structListSz - 2);
		}
		convertStructToTexAssertHelper(structList, structListSz, firstStruct, lastStruct);
	}

	/**
	 * @param structList
	 * @param structListSz
	 * @param firstStruct
	 * @param lastStruct
	 */
	private static void convertStructToTexAssertHelper(List<Struct> structList, int structListSz, Struct firstStruct,
			Struct lastStruct) {
		//if(true) throw new IllegalStateException(lastStruct.containsLatexStruct() +" " + lastStruct.toString());
		if((1 == structListSz || firstStruct.containsPos("hyp") || firstStruct.containsPos("if"))
				&& lastStruct.containsLatexStruct()){
			//if(true) throw new IllegalStateException();
			if(!lastStruct.has_child()){
				String tex = lastStruct.struct().get("tex");
				tex = null == tex ? "" : tex;
				StructA<String, String> convertedStructA = new StructA<String, String>(lastStruct.nameStr(), 
						NodeType.STR, tex, NodeType.STR, "texAssert");
				structList.set(structListSz - 1, convertedStructA);
			}				
			//lastStruct.set_type("texAssert");
		}
	}
	
	protected static String getChildRelationStringFromStructPrev1(Struct struct){
		String childRelationStr;// = struct.prev1().toString();
		if(struct.prev1NodeType().equals(NodeType.STR)){
			childRelationStr = struct.prev1().toString();
		}else{
			childRelationStr = ((Struct)struct.prev1()).nameStr();
		}
		return childRelationStr;
	}
	
	/**
	 * @param mathIndexList
	 * @param pairs
	 * @param pairsLen
	 */
	public static void updatePosInPairsList(List<Integer> mathIndexList, List<Pair> pairs) {
		Pair curpair;
		int pairsLen = pairs.size();
		//turn "symb" pos into "ent" if followed by "pre",
		//e.g. "given $x$ in a compact space", but not if preceded by an ent.
		for (int index = 0; index < pairsLen; index++) {		
			curpair = pairs.get(index);
			String curWord = curpair.word();	
			String curPos = curpair.pos();				
			if(curPos.equals("symb") 
					&& index < pairsLen-1 && pairs.get(index+1).pos().equals("pre") 
					&& index > 0 && !pairs.get(index-1).pos().equals("ent") ){
				curpair.set_pos("ent");
				mathIndexList.add(index);
			}
			//changing num to either adj or ent
			if("num".equals(curPos)){
				if(index < pairsLen-1){
					if(pairs.get(index+1).pos().equals("ent")){
						curpair.set_pos("adj");
					}else{
						curpair.set_pos("ent");
						mathIndexList.add(index);
					}
				}else{
					curpair.set_pos("ent");
					mathIndexList.add(index);
				}
			}
		}
		
		if(pairsLen > 0){
			Pair lastPair = pairs.get(pairsLen-1);
			String lastPairPos = lastPair.pos();
			if("verb".equals(lastPairPos) || "vbs".equals(lastPairPos)){
				lastPair.set_pos("verbAlone");
			}
		}
	}
	

	/**
	 * @param parseState
	 * @param inputStructList
	 * @param structListList
	 */
	public static void convertToTexAssert(ParseState parseState, List<Struct> inputStructList,
			List<StructList> structListList) {
		//if only one ent,
		//e.g. "then $ $", misrepresented StructH as StructA, 
		//if should have been an "assert".
		if(structListList.size() < 3){
			
			//go through structList see 
			//boolean couldConvertToAssert = false;
			List<Struct> entSubstitutedStructList = new ArrayList<Struct>(inputStructList);
			Struct toBeConvertedStruct = null;
			
			for(StructList sList : structListList){
				Struct struct = sList.get(0);
				//entToAssertStructList.add(struct);
				if(struct.isLatexStruct()){
					//if(true) throw new IllegalStateException(structListList.toString());
					assert !struct.isStructA() 
						: "Struct must be StructH to be latexStruct!";						
					//couldConvertToAssert = true;
					toBeConvertedStruct = struct;
					break;
				}						
			}
			System.out.println("ThmP1 - toBeConvertedStruct " + toBeConvertedStruct);
			if(null != toBeConvertedStruct){
				
				//need to convert toBeConvertedStruct to a StructA 
				//with type "assert".						
				String toBeConvertedStructName = toBeConvertedStruct.nameStr();
				
				for(int k = 0; k < entSubstitutedStructList.size(); k++){
					
					Struct structToSubstitute = entSubstitutedStructList.get(k);
					if(structToSubstitute.nameStr().equals(toBeConvertedStructName)){
						//if s already has child, then means probably should not turn into assertion,
						//since most children are appended during mx-building
						if(structToSubstitute.has_child()){
							break;
						}
						
						//StructH should not have any properties .  Look through properties of toBeConvertedStruct?
						StructA<String, String> convertedStructA = new StructA<String, String>(toBeConvertedStructName, 
								NodeType.STR, "", NodeType.STR, "texAssert");
						
						convertedStructA.set_parentStruct(structToSubstitute.parentStruct());
						//convertedStructA.set_maxDownPathScore(structToSubstitute.maxDownPathScore());
						
						entSubstitutedStructList.set(k, convertedStructA);
						//if(true) throw new IllegalStateException(inputStructList.toString());
						//isReparse = true;
						parseState.setTokenList(entSubstitutedStructList);
						//don't set isReparse, so to allow defluffing in the recursion call.
						System.out.println("~~REPARSING with assert");
						ThmP1.parse(parseState);						
						System.out.println("~~REPARSING with assert DONE");
					}
				}						
			}
		}
	}
}
