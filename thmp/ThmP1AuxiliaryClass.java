package thmp;

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
}
