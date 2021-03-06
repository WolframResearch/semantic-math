package thmp.parse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wolfram.jlink.Expr;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import thmp.parse.ParseToWLTree.WLCommandWrapper;
import thmp.parse.Struct.ChildRelation;
import thmp.parse.WLCommand.PosTerm;
import thmp.utils.ExprUtils;
import thmp.utils.ExprUtils.AssocExprWrapper;
import thmp.utils.ExprUtils.ExprWrapper;
import thmp.utils.ExprUtils.RuleExprWrapper;

/*
 * Struct to contain entities in sentence
 * to be parsed
 */

public abstract class Struct implements Serializable{
	//remove this 
	//public int WLCommandStrVisitedCount;

	/*
	public Struct(K struct, String type){
		this.struct = struct;
		this.type = type;
	}
	
	public Struct(Struct<K> prev1, Struct<K> prev2, String type){
		this.prev1 = prev1;
		this.prev2 = prev2;
		this.type = type;
	}
	*/
	
	private static final long serialVersionUID = 1L;
	protected static final Pattern CONJ_DISJ_PATTERN = Pattern.compile("(?:conj|disj)_(.+)");
	
	/* Set of commands in which this Struct has already been used. */
	private transient Set<WLCommand> usedInCommandsSet = new HashSet<WLCommand>();
	//whether this struct has been used in another component
	//e.g. Action[ MathObj{group, $G$} , MathObj{{subgroup, $H$, by conjugation}} , MathObj{conjugation}
	//want to exclude "by conjugation" from middle term. Must clear this flag for new dfs walkdowns.
	//Should only set if command is satisfied.
	//private boolean usedInOtherCommandComponent;
	
	//whether this struct has a hypothesis construct (if..., assume...)
	//down the tree. Used for constructing ParseStruct tree.
	private boolean containsHyp;
	
	//index in the struct (and token) array without TeX, for syntaxnet.
	private int noTexTokenListIndex; 
	//cache, for comparison
	private int numCoincidingStruct = -1;
	
	//logical parent, e.g. $p$ such that $p$ is prime. the parent of prime
	//private Struct logicalParent;
	
	/*  childRelationType to its parent, if this struct is a child. */
	private ChildRelationType childRelationType = ChildRelationType.OTHER;
	
	private Article article = Article.NONE;
	
	/**
	 * Should be set during dfs when building up parse tree and commands.
	 * @param containsHyp Whether this struct has a hypothesis construct (if..., assume...)
	 * down the tree. Used for constructing ParseStruct tree.
	 */
	public void setContainsHyp(boolean containsHyp){
		this.containsHyp = containsHyp;
	}
	
	/**
	 * Whether this struct has a hypothesis construct (if..., assume...)
	 * down the tree. Used for constructing ParseStruct tree.
	 * @return
	 */
	public boolean containsHyp(){
		return this.containsHyp;
	}
	/**
	 * childRelationType to its parent, if this struct is a child.
	 * Default value is ChildRelationType.OTHER.
	 * @return
	 */
	public ChildRelationType childRelationType(){
		return this.childRelationType;
	}
	
	public void set_childRelationType(ChildRelationType type){
		this.childRelationType = type;
	}
	//change to array index!!
	public void setNoTexTokenListIndex(int index){
		this.noTexTokenListIndex = index;
	}
	/**
	 * index in the token list without TeX, for syntaxnet.
	 * @return
	 */
	public int noTexTokenListIndex(){
		return this.noTexTokenListIndex;
	}
	
	public void setNumCoincidingRelationIndex(int num){
		this.numCoincidingStruct = num;
	}
	public int numCoincidingRelationIndex(){
		return numCoincidingStruct;
	}
	/*public void setNoTexStructAr(Struct[] ar){
		this.noTexTokenAr = ar;
	}*/
	
	/**
	 * index in the token list without TeX, for syntaxnet.
	 * @return
	 */
	/*public Struct[] noTexStructAr(){
		return this.noTexTokenAr;
	}*/
	
	/**
	 * Does this tree contain 
	 * Avg performance is O(log n), where n is number of nodes in parse tree,
	 * with small constant factor.
	 * Look through ancestors.
	 * @return
	 */
	public boolean parseTreeContainsHyp(){
		Struct curStruct = this;
		while(curStruct != null){
			if(curStruct.containsHyp()){
				return true;
			}
			curStruct = curStruct.parentStruct();
		}
		return false;
	}
	
	public void setArticle(Article article){
		this.article = article;
	}
	
	/**
	 * Article of struct, e.g. "a", "an", "the" 
	 * Default is NONE.
	 * @return
	 */
	public Article article(){
		return this.article;
	}
	
	/**
	 * 
	 * @return whether this struct is StructA or not
	 */
	public boolean isStructA(){
		boolean isStructA = this.struct() == null;
		//enforcing principle that all StructH should be of type "ent"		
		assert(isStructA || this.type().equals("ent"));
		return isStructA;
	}
	
	/**
	 * Set whether this struct has been used in another component.
	 * e.g. Action[ MathObj{group, $G$} , MathObj{{subgroup, $H$, by conjugation}} , MathObj{conjugation}
	 * want to exclude "by conjugation" from middle term. Must clear this flag for new dfs walkdowns.
	 * Should only set if command is satisfied.
	 */
	public void clearUsedInCommandsSet(){
		//this.usedInOtherCommandComponent = usedInOtherCommandComponent;
		//if(!usedInOtherCommandComponent){
			this.usedInCommandsSet = new HashSet<WLCommand>();
		//}
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
	}
	
	/**
	 * Adds to set of WLCommand's in which this struct has already been used. To 
	 * avoid duplicate usage in same command.
	 * @param usedInCommand
	 */
	public void add_usedInCommand(WLCommand usedInCommand){
		this.usedInCommandsSet.add(usedInCommand);
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		//System.out.println("struct used: " + this);		
	}
	/**
	 * Whether this struct has been used in another component (of the same command).
	 * Currently only used in the case when a child of a StructH has
	 * been used as another *entire* component in same command. "Entire" meaning
	 * a whole PosTerm. Checks prev1 and prev2 as well.
	 * @return
	 */
	public boolean usedInOtherCommandComponent(WLCommand curCommand){	
		
		//System.out.println("this.usedInCommandsSet: " + this.usedInCommandsSet);
		//System.out.println(usedInCommandsSet.contains(curCommand) + "curCommand " + curCommand);
		//for(WLCommand c : usedInCommandsSet){
			//System.out.println(curCommand.equals(c) + " hc:  " +curCommand.hashCode() + " " + c.hashCode());
		//}
		WLCommand copyWithOutOptTermsCommand = curCommand.getCopyWithOutOptTermsCommand();		
		boolean usedInOtherComponent = usedInOtherCommandComponent(this, curCommand, copyWithOutOptTermsCommand);
		
		if(!usedInOtherComponent && this.prev1NodeType().isTypeStruct()){
			usedInOtherComponent = usedInOtherCommandComponent((Struct)this.prev1(), curCommand, copyWithOutOptTermsCommand);
		}
		if(!usedInOtherComponent && this.prev2NodeType().isTypeStruct()){
			usedInOtherComponent = usedInOtherCommandComponent((Struct)this.prev2(), curCommand, copyWithOutOptTermsCommand);
		}
		return usedInOtherComponent;
	}
	
	public boolean usedInOtherCommandComponentInDepth(WLCommand curCommand){	
		return usedInOtherCommandComponentInDepth(this, curCommand);
	}
	/**
	 * Analogous to usedInOtherCommandComponent, but checks down to every leaf.
	 * @param curCommand
	 * @return
	 */
	private boolean usedInOtherCommandComponentInDepth(Struct struct, WLCommand curCommand){	
		
		//System.out.println("this.usedInCommandsSet: " + this.usedInCommandsSet);
		//System.out.println(usedInCommandsSet.contains(curCommand) + "curCommand " + curCommand);
		//for(WLCommand c : usedInCommandsSet){
			//System.out.println(curCommand.equals(c) + " hc:  " +curCommand.hashCode() + " " + c.hashCode());
		//}
		WLCommand copyWithOutOptTermsCommand = curCommand.getCopyWithOutOptTermsCommand();		
		boolean usedInOtherComponent = usedInOtherCommandComponent(struct, curCommand, copyWithOutOptTermsCommand);
		
		if(!usedInOtherComponent && struct.prev1NodeType().isTypeStruct()){
			usedInOtherComponent = usedInOtherCommandComponent((Struct)struct.prev1(), curCommand, copyWithOutOptTermsCommand);
		}
		if(!usedInOtherComponent && struct.prev2NodeType().isTypeStruct()){
			usedInOtherComponent = usedInOtherCommandComponent((Struct)struct.prev2(), curCommand, copyWithOutOptTermsCommand);
		}
		if(!usedInOtherComponent){
			if(struct.prev1NodeType().isTypeStruct()){
				usedInOtherComponent = usedInOtherCommandComponentInDepth((Struct)struct.prev1(), curCommand);				
			}
			if(!usedInOtherComponent && struct.prev2NodeType().isTypeStruct()){
				usedInOtherComponent = usedInOtherCommandComponentInDepth((Struct)struct.prev2(), curCommand);				
			}
		}
		return usedInOtherComponent;
	}

	private boolean usedInOtherCommandComponent(Struct struct, WLCommand curCommand, WLCommand copyWithOutOptTermsCommand){	
		//WLCommand copyWithOutOptTermsCommand = curCommand.getCopyWithOutOptTermsCommand();
		if(null != copyWithOutOptTermsCommand){			
			return this.usedInCommandsSet.contains(curCommand) || this.usedInCommandsSet.contains(copyWithOutOptTermsCommand);
		}
		return struct.usedInCommandsSet.contains(curCommand);		
	}
	
	public abstract Struct previousBuiltStruct();

	public abstract Struct posteriorBuiltStruct();
	
	public abstract void set_previousBuiltStruct(Struct previousBuiltStruct);
	
	public abstract void set_posteriorBuiltStruct(Struct posteriorBuiltStruct);
	
	public abstract Struct structToAppendCommandStr();

	public abstract void set_structToAppendCommandStr(Struct structToAppendCommandStr);
	
	public abstract int WLCommandStrVisitedCount();
	
	public abstract void clear_WLCommandStrVisitedCount();
	
	//public abstract boolean commandVisited();
	public abstract void clear_commandBuilt();
	
	/**
	 * Set list of structs in mx that this struct is attached to.
	 * @param structList
	 */
	public abstract void set_structList(StructList structList);

	/**
	 * Set parent pointer of this struct
	 */
	public abstract void set_parentStruct(Struct parent);	
	public abstract Struct parentStruct();
	
	/*public void set_logicalParentStruct(Struct parent){
	
	}
	
	public Struct logicalParentStruct(){
		
	}*/

	/**
	 * Only meaningful for StructH.
	 * @param prev
	 */
	public abstract void set_possessivePrev(Struct prev);
	
	public abstract Struct possessivePrev();
	
	/**
	 * Sets the depth from root of the tree. Root has depth 0. *Not* intrinsic to the Struct,
	 * depends on the DFS path.
	 */
	public abstract void set_dfsDepth(int depth);
	
	public abstract int dfsDepth();
	
	//returns probability of relation in Rule
	public abstract double score();
	
	public abstract boolean has_child();
	
	/**
	 * If any descendent has a child.
	 * @return
	 */
	public boolean descendantHasChild(){
		
		if(!this.isStructA()){
			return this.has_child();
		}
		
		boolean hasOffspring = false;
		if(this.prev1NodeType().isTypeStruct()){
			hasOffspring = ((Struct)this.prev1()).descendantHasChild();
		}
		
		if(hasOffspring) return true;
			
		if(this.prev2NodeType().isTypeStruct()){
			hasOffspring |= ((Struct)this.prev2()).descendantHasChild();
		}
		return hasOffspring;
	}
	
	//Simple toString to return the bare minimum to identify this Struct.
	//To be used in ParseToWLTree.
	public abstract String simpleToString(boolean includeType, WLCommand curCommand, PosTerm triggerPosTerm, 
			PosTerm curPosTerm, List<Expr> exprList);
	public abstract String simpleToString(boolean includeType, WLCommand curCommand);
	public abstract String simpleToString(boolean includeType, WLCommand curCommand, List<Expr> exprList);
	
	/**
	 * E.g. is
	 * @param triggerPosTerm
	 * @param curPosTerm
	 * @return
	 */
	String retrievePosTermPptStr(PosTerm triggerPosTerm, PosTerm curPosTerm) {
		String makePptStr = "";
		Struct triggerPosTermStruct;
		if(null != triggerPosTerm && curPosTerm.isPropertyTerm() 
				&& null != (triggerPosTermStruct=triggerPosTerm.posTermStruct())){
			String triggerNameStr = triggerPosTermStruct.nameStr();
			Matcher matcher;
			if((matcher=WLCommand.NEGATIVE_TRIGGER_PATTERN.matcher(triggerNameStr)).find()){
				makePptStr = matcher.replaceAll("");
			}else{
				makePptStr = triggerNameStr;
			}
		}
		return makePptStr;
	}
	
	//public abstract String simpleToString2(boolean includeType, WLCommand curCommand);
	
	//public abstract void append_WLCommandStr(String WLCommandStr);

	public abstract void setContextVecEntry(int structParentIndex, Map<Integer, Integer> contextVecMap, boolean adjustVecFromCommand);
	
	/**
	 * Sets WLCommandStr to null.
	 */
	//public abstract void clear_WLCommandStr();
	
	//public abstract String WLCommandStr();
	
	public abstract WLCommandWrapper add_WLCommandWrapper(WLCommand newCommand);
	
	public abstract List<WLCommandWrapper> WLCommandWrapperList();
	
	public abstract void clear_WLCommandWrapperList();
	
	/**
 	 * number of units down from this Struct (descendents), used for tie-breaking.
 	 * StructH counts as one unit. Lower numUnits wins, since it means more consolidation.
 	 * e.g. children of MathObj's grouped together with the MathObj.
 	 * The lower the better.
	 * @return
	 */
	public abstract int numUnits();
	
	public abstract double maxDownPathScore();
	
	public abstract void set_maxDownPathScore(double pathScore);
	
	/**
	 * List of additional pos, e.g. "prime" has pos adj, but is 
	 * often used as an "ent". "type()" contains primary pos.
	 * @return
	 */
	public abstract Set<String> extraPosSet();
	/**
	 * Add additional parts of speech to this struct.
	 */
	public abstract void addExtraPos(String pos);
	
	/**
	 * Return value cannot be null.
	 * @return
	 */
	public abstract List<String> contentStrList();
	
	/**
	 * What most qualifies as the Struct's name, for
	 * StructH, it's "name" entry in the struct map.
	 * For StructA, it's the prev1 string, if it is String.
	 */
	public abstract String nameStr();
	
	public abstract StructList get_StructList();
	
	public abstract void set_score(double score);
	
	//to be overridden
	public abstract void set_type(String type);
	
	/**
	 * Makes deep copy, including children.
	 * @return
	 */
	public abstract Struct copy();
	
	//to be overridden
	public abstract String type();
	
	//For measuring spans of posterm lists.
	public abstract int getPosTermListSpan();
	
	/**
	 * Retrieves type without conjunction or disjunction.
	 * @return
	 */
	public String typeWithNoConjDisj(){
		Matcher matcher;
		if((matcher=CONJ_DISJ_PATTERN.matcher(this.type())).matches()){
			return matcher.group(1);
		}else{
			return this.type();
		}
	}
	
	public boolean isFoodStruct(){
		return false;
	}
	
	public boolean containsPos(String pos){
		return (null != this.type() && this.type().equals(pos)) 
				|| (null != this.extraPosSet() && this.extraPosSet().contains(pos));
	}
	
	public abstract boolean isLeafNodeCouldHaveChildren();
	//to be overwritten in StructH
	public abstract List<Struct> children();

	//to be overwritten in StructH
	public abstract List<ChildRelation> childRelationList();
	
	protected abstract void setHasChildToTrue();
	
	protected boolean contentEquals(Struct other){
		return false;
	}
	
	public abstract Set<String> getPropertySet();
	
	public void add_child(Struct child, ChildRelation relation){
		assert(!this.equals(child));		
		/*if(this.contentEquals(child)){			
			return;
		}*/
		//System.out.println("Struct - add_child" + Arrays.deepToString(Thread.currentThread().getStackTrace()));
		this.setHasChildToTrue();
		/* these two should always be modified together, better to put
		 * child + relation in a static nested class*/
		children().add(child);
		childRelationList().add(relation);
		child.set_parentStruct(this);
	}
	
	public void copyChildrenToStruct(Struct targetStruct){
		if(!this.has_child()){
			return;
		}		
		List<Struct> childrenList = children();
		List<ChildRelation> childRelationList = childRelationList();
		int childrenListSz = childrenList.size();
		assert childrenListSz == childRelationList.size();
		
		for(int i = 0; i < childrenListSz; i++){	
			Struct child = childrenList.get(i);
			targetStruct.add_child(child, childRelationList.get(i));
			//parent is not well-defined, e.g. could have multiple parents.
			//child.set_parentStruct(targetStruct);
		}
	}
	public abstract StructA<? extends Object, ? extends Object> copyToStructA(String newType);
	
	/**
	 * @param includeType
	 * @param curCommand
	 * @param sb StringBuilder to append child qualifier string to.
	 * @param childAssocWrapperList List to be filled with one association,
	 * e.g. "Qualifiers" -> {"with", Math["Name"->"axiom"]}.
	 * @return child String
	 */
	protected String appendChildrenQualifierString(boolean includeType, WLCommand curCommand, //List<RuleExprWrapper> childRuleWrapperList
			List<Expr> childLevelExprList) {
		List<Struct> children = this.children();
		List<ChildRelation> childRelationList = this.childRelationList();
		int childrenSize = children.size();
		int nontrivialChildrenStrCounter = 0;
		//System.out.println("appendChildrenQualifierStrin struct " + this +" children: " + children);
		StringBuilder childSb = new StringBuilder(30);
		if(childrenSize > 0){	
			List<Expr> childExprList = new ArrayList<Expr>();
			for(int i = 0; i < childrenSize; i++){
				Struct child = children.get(i);
				String childRelationStr = childRelationList.get(i).childRelationStr;
				/*if(child.WLCommandWrapperList() != null){
					continue;
				}*/
				//str += ", ";
				//str += childRelation.get(i) + " ";	
				//System.out.println("^^^cur child: " + child);
				//System.out.println("Used? "+ child.usedInOtherCommandComponent(curCommand) + " child: " +child);
				
				//only append curChidRelation if child is a StructH, to avoid
				//including the relation twice, eg in case child is of type "prep"
				//if this child has been used in another component of the same command.	
				//System.out.println("Struct - child.usedInOtherCommandComponent(curCommand)" + child.usedInOtherCommandComponent(curCommand));
				/*if(child.type().equals("assert")){
					System.out.print("Struct.java - assert not used elsewhere");
				}*/
				if(!child.usedInOtherCommandComponentInDepth(curCommand)){
					List<Expr> exprList = new ArrayList<Expr>();
					String childStr = child.simpleToString(includeType, curCommand, exprList);						
					if(!childStr.matches("\\s*")){
					//System.out.println("Childstr " + childStr);
						nontrivialChildrenStrCounter++;
						//don't want "symb", e.g. $G$ with $H$ prime. 
						/*childRelationStr = (child.isStructA() && !child.type().equals("symb"))
								//e.g. "field which is perfect", don't want "which"	
								? "" : childRelationStr;*/
						
						//System.out.println("\n **^^^*** childRelation" + childRelationStr);
						//e.g. "Qualifiers" -> {"with", Math["Name"->"axiom"]}
						
						if(childRelationStr.equals("")){
							//childSb.append("{").append(childStr).append("}");
							childSb.append(childStr).append(", ");							
						}else{
							childSb.append("{\"").append(childRelationStr).append("\", ").append(childStr).append("}, ");
							exprList.add(0, new Expr(childRelationStr));
							//childExprList.add(new Expr(childRelationStr));
						}
						if(exprList.size() == 1) {
							childExprList.add(exprList.get(0));
						}else {
							childExprList.add(ExprUtils.listExpr(exprList));
						}
						//childExprList.addAll(exprList);
						//WLCommand.increment_commandNumUnits(curCommand, this); //HERE
					}
				}
			}
			int childSbLen = childSb.length();
			if(2 < childSbLen){
				childSb = childSb.delete(childSbLen-2, childSbLen);
				if(nontrivialChildrenStrCounter > 1){
					childSb.insert(0, ", \"Qualifiers\" -> {").append("}");					
				}else{
					childSb.insert(0, ", \"Qualifiers\" -> ");					
				}
				//RuleExprWrapper childExprWrapper = new RuleExprWrapper(new Expr("Qualifier"), ExprUtils.listExpr(childExprList));				
				//List<RuleExprWrapper> ruleExprWrapperList = new ArrayList<RuleExprWrapper>();
				//ruleExprWrapperList.add(childExprWrapper);
				//childAssocWrapperList.add(new AssocExprWrapper(ruleExprWrapperList));	
				//childRuleWrapperList.add(childExprWrapper);	
				childLevelExprList.add(ExprUtils.qualifierExpr(childExprList));
			}	
		}
		return childSb.toString();
	}
	
	
	// to be overriden
	public abstract Map<String, String> struct();
	
	public abstract String toString();

	public void set_prev1(Object prev1){		
	}
	
	public abstract void set_prev1(String str);
	
	//not abstract, because not applicable to StructH
	public void set_prev2(Object obj){		
	}
	
	/**
	 * Whether this structH arises from a latex expression, e.g. $x > 0$.
	 * Used to convert ent into assert if necessary. 
	 */
	public boolean isLatexStruct() {
		return false;
	}
	
	/**
	 * Only applicable for StructH. Either $...$ or 
	 * has absorbed an $...$.
	 * @return
	 */
	public boolean containsLatexStruct() {
		return false;
	}

	public abstract Object prev1();
	
	public abstract Object prev2();
		
	public abstract String present(String str);
	
	/**
	 * Guaranteed non-null
	 */
	public abstract NodeType prev1NodeType();
	/**
	 * Guaranteed non-null
	 */
	public abstract NodeType prev2NodeType();
	
	/**
	 * Possible types of prev1 and prev2
	 */
	public enum NodeType{
		//NONE type indicates that this is none of the previous ones
		//used for instance for StructH.
		STR, STRUCTA, STRUCTH, NONE;
		
		/**
		 * Whether has type either StructA or StructH
		 * @return
		 */
		public boolean isTypeStruct(){
			return this.equals(STRUCTA) || this.equals(STRUCTH);
		}
	}

	
	/**
	 * Definite (the) or indefinite (a/an) article
	 */
	enum Article{
		//NONE type indicates that this is none of the previous ones
		THE, A, NONE;
		
		static Article getArticle(String articleStr){
			Article article;
			switch(articleStr){
			case "a":
				article = A;
				break;
			case "an":
				article = A;
				break;
			case "the":
				article = THE;
				break;
			default:
				article = NONE;
			}
			return article;
		}
		
		@Override
		public String toString(){
			switch(this){
			case A:
				return "a";
			case THE:
				return "the";
			default:
				return "";
			}
		}
	}
	
	/**
	 * Class for describing parent to child relation.
	 * e.g. "prime ideal of $R$" -- "of" is the relation String.
	 * 
	 */
	public static class ChildRelation implements Serializable{
		
		private static final long serialVersionUID = 1L;
		//private static final long serialVersionUID = 3635527039195547129L;

		String childRelationStr;
		
		public ChildRelation(String relation){
			//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
			this.childRelationStr = relation;
		}
		
		public boolean isHyp(){
			return false;
		}
		
		public String childRelationStr(){
			return this.childRelationStr;
		}
		
		/**
		 * Type of ChildRelation, determined by the relation 
		 * word, e.g. "over", "of", vs "such as", "which is" etc.
		 * @return
		 */
		public ChildRelationType childRelationType(){
			//the default is prep
			return ChildRelationType.PREP;
		}
		
		@Override
		public String toString(){
			return this.childRelationStr;
		}
		
		/**
		 * e.g. "ideal which is prime" -- "which is" is the relation String,
		 * and has type "hyp"
		 */
		public static class HypChildRelation extends ChildRelation{
			
			private static final long serialVersionUID = 2861506844911474840L;
			private ChildRelationType childRelationType= ChildRelationType.HYP;
			
			public HypChildRelation(String relation){
				super(relation);
			}
			
			public ChildRelationType childRelationType(){
				return this.childRelationType;
			}
			
			@Override
			public boolean isHyp(){
				return true;
			}
		}
		
		/**
		 * e.g. "field over $Q$" -- "over" is the relation String,
		 * and has type "pre"
		 */
		public static class PrepChildRelation extends ChildRelation{
			
			private static final long serialVersionUID = -2343529901137078313L;
			private ChildRelationType childRelationType = ChildRelationType.PREP;
			
			public PrepChildRelation(String relation){
				super(relation);
			}
			
			public ChildRelationType childRelationType(){
				return this.childRelationType;
			}
		}
		
	}
	
	/**
	 * enum for child relation. 
	 *
	 */
	public enum ChildRelationType{
		//preppy child or hippie child?
		PREP, HYP, OTHER;
	}
	
}
