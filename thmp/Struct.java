package thmp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import thmp.ParseToWLTree.WLCommandWrapper;

/*
 * Struct to contain entities in sentence
 * to be parsed
 */

public abstract class Struct {
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

	private Article article = Article.NONE;
	
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
	
	public abstract Struct previousBuiltStruct();

	public abstract Struct posteriorBuiltStruct();
	
	public abstract void set_previousBuiltStruct(Struct previousBuiltStruct);
	
	public abstract void set_posteriorBuiltStruct(Struct posteriorBuiltStruct);
	
	public abstract Struct structToAppendCommandStr();

	public abstract void set_structToAppendCommandStr(Struct structToAppendCommandStr);
	
	public abstract int WLCommandStrVisitedCount();
	
	public abstract void clear_WLCommandStrVisitedCount();
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
	public abstract String contentStr();
	
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
	public abstract List<String> childRelation();
	
	//to be overwritten in StructH
	public abstract void add_child(Struct child, String relation);
	
	// to be overriden
	public abstract Map<String, String> struct();
	
	public abstract String toString();

	public abstract void set_prev1(String str);
	
	//not abstract, because not applicable to StructH
	public void set_prev2(String str){		
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
	
	
}
