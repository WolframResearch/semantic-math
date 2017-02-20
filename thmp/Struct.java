package thmp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import thmp.ParseToWLTree.WLCommandWrapper;

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
	
	/*  childRelationType to its parent, if this struct is a child.
	 */
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
	
	public ChildRelationType childRelationType(){
		return this.childRelationType;
	}
	
	public void set_childRelationType(ChildRelationType type){
		this.childRelationType = type;
	}
	
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
		if(!isStructA){
			assert(this.type().equals("ent"));
		}
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
	 * a whole PosTerm.
	 * @return
	 */
	public boolean usedInOtherCommandComponent(WLCommand curCommand){	
		
		//System.out.println("this.usedInCommandsSet: " + this.usedInCommandsSet);
		//System.out.println(usedInCommandsSet.contains(curCommand) + "curCommand " + curCommand);
		//for(WLCommand c : usedInCommandsSet){
			//System.out.println(curCommand.equals(c) + " hc:  " +curCommand.hashCode() + " " + c.hashCode());
		//}
		WLCommand copyWithOutOptTermsCommand = curCommand.getCopyWithOutOptTermsCommand();
		if(null != copyWithOutOptTermsCommand){			
			return this.usedInCommandsSet.contains(curCommand) || this.usedInCommandsSet.contains(copyWithOutOptTermsCommand);
		}
		return this.usedInCommandsSet.contains(curCommand);
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
	public abstract String simpleToString(boolean includeType, WLCommand curCommand);
	
	public abstract String simpleToString2(boolean includeType, WLCommand curCommand);
	
	//public abstract void append_WLCommandStr(String WLCommandStr);

	public abstract void setContextVecEntry(int structParentIndex, int[] contextVec);
	
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
	
	public abstract Struct copy();
	
	//to be overridden
	public abstract String type();
	
	//to be overwritten in StructH
	public abstract List<Struct> children();

	//to be overwritten in StructH
	public abstract List<ChildRelation> childRelationList();
	
	//to be overwritten in StructH. Not abstract, not applicable
	//to StructA
	public void add_child(Struct child, ChildRelation relation){		
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

	public abstract Object prev1();
	
	public abstract Object prev2();
		
	public abstract String present(String str);
	
	public abstract NodeType prev1NodeType();
	public abstract NodeType prev2NodeType();
	
	/**
	 * Possible types of prev1 and prev2
	 */
	enum NodeType{
		//NONE type indicates that this is none of the previous ones
		//used for instance for StructH.
		STR, STRUCTA, STRUCTH, NONE;
		
		/**
		 * Whether has type either StructA or StructH
		 * @return
		 */
		boolean isTypeStruct(){
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
	}
	
	/**
	 * Class for describing parent to child relation.
	 * e.g. "prime ideal of $R$" -- "of" is the relation String.
	 * 
	 */
	public static class ChildRelation implements Serializable{
		
		private static final long serialVersionUID = 1L;

		String childRelationStr;
		
		public ChildRelation(String relation){
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
