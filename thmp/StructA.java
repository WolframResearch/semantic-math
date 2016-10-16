package thmp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import thmp.ParseToWLTree.WLCommandWrapper;
import thmp.Struct.NodeType;

public class StructA<A, B> extends Struct{

	//a Struct can correspond to many MatrixPathNode's, but each MatrixPathNode 
	//corresponds to one unique Struct.
	//I.e. one-to-many map between Struct's and MatrixPathNode's
	
	private A prev1; 
	private B prev2; 
	//parentStruct is *not* unique! Depends on which DFS path we take.
	private Struct parentStruct;
	//Depth from root of the tree. Root has depth 0. *Not* intrinsic to the Struct, 
	//depends on the DFS path.
	private int depth;
	//score for this structA, to indicate likelihood of relation in Rule
	//Ranges over (0, 1]. 1 by default
	private double score;
	private String type; //or, and, adj, pro etc, cannot ent
	//private String type1; //type of prev1, , al, string etc. Is this used??
	private String type2; //type of prev2. Also not used!
	private NodeType PREV1_TYPE;
	private NodeType PREV2_TYPE;
	
	//list of Struct at mx element, to which this Struct belongs
	//pointer to mx.get(i).get(j)
	//if not null, means this is head of some parsed WLCommand. 
	//private String WLCommandStr;
	//WLCommand associated with this Struct, should have corresponding WLCommandStr.
	//Perhaps group the WLCommandStr with this into the WLCommand?
	private List<WLCommandWrapper> WLCommandWrapperList;
	//how many times this Struct has been part of a WLCommand.
	private int WLCommandStrVisitedCount;
	//pointer to the head of a previously built Struct that already
	//contains this Struct, so no need to build this Struct again into the current 
	//WLCommand in build(), remember to reset to null after iterating through
	private Struct previousBuiltStruct;
	private Struct posteriorBuiltStruct;
	//the head Struct (to append to) of a WLCommand this Struct currently belongs to.
	//Not intrinsic to this Struct!
	private Struct structToAppendCommandStr;
	private StructList structList;
	//includes this/current Struct's score!
	private double DOWNPATHSCOREDEFAULT = 1;
	private double maxDownPathScore = DOWNPATHSCOREDEFAULT;
	
	//number of units down from this Struct (descendents), used for tie-breaking.
	//StructH counts as one unit. Lower numUnits wins, since it means more consolidation.
	//e.g. children of MathObj's grouped together with the MathObj.
	private int numUnits;
	//don't need mxPathNodeList. The path down from this Struct should 
	//be unique. It's the parents' paths to here that can differ
	//private List<MatrixPathNode> mxPathNodeList;
	
	//is this ever needed?
	public StructA(A prev1, NodeType prev1Type, B prev2, NodeType prev2Type, String type, StructList structList){		
		this(prev1, prev1Type, prev2, prev2Type, type);
		this.structList = structList;
	}
	
	public StructA(A prev1, NodeType prev1Type, B prev2, NodeType prev2Type, String type){	
		this.PREV1_TYPE = prev1Type;
		this.PREV2_TYPE = prev2Type;
		this.prev1 = prev1;		
		this.prev2 = prev2;
		this.type = type; 
		this.numUnits = 1;
		this.score = 1;
	}
	
	/**
	 * @param prev1
	 * @param prev2
	 * @param type
	 * @param score
	 * @param structList  pointer to list of Struct's containing this.
	 * @param downPathScore 
	 * @param numUnits
	 */
	public StructA(A prev1, NodeType prev1Type, B prev2, NodeType prev2Type, String type, double score, StructList structList, 
			double downPathScore, int numUnits){
		this.PREV1_TYPE = prev1Type;
		this.PREV2_TYPE = prev2Type;
		this.prev1 = prev1;		
		this.prev2 = prev2;
		this.type = type; 
		this.structList = structList;
		this.score = score;
		this.maxDownPathScore = downPathScore;
		this.numUnits = numUnits;
		//this.mxPathNodeList = new ArrayList<MatrixPathNode>();
	}
	
	public NodeType prev1NodeType(){
		return PREV1_TYPE;
	}
	
	public NodeType prev2NodeType(){
		return PREV2_TYPE;
	}
	
	//this method should never be called on StructA
	//Would be safer to remove from abstract class, cast
	//the Struct to StructH in ThmP1, and call struct() on that.
	//That way ClassCastException will be generated instead of
	//problems down the road by caused by null.
	public Map<String, String> struct(){		
		return null;
	}
	
	/**
	 * Shallow copy. 
	 */
	public StructA<A, B> copy(){
		//shallow copy of structlist
		StructList copiedStructlist = this.structList.copy();
		StructA<A, B> newStruct = new StructA<A, B>(this.prev1, this.PREV1_TYPE, 
				this.prev2, this.PREV2_TYPE, this.type, copiedStructlist);
		newStruct.maxDownPathScore = this.maxDownPathScore;
		newStruct.numUnits = this.numUnits;
		newStruct.score = this.score;
		//newStruct.WLCommandStr = this.WLCommandStr;
		return newStruct;
	}
	
	/**
	 * Retrieves the left-most child of this struct, which should
	 * be a String based on the structure of Struct.
	 * @return
	 */
	public String getLeftMostChild(){
		StructA<?,?> curStructA = this;
		while(curStructA.prev1 instanceof StructA){
			curStructA = (StructA<?,?>)curStructA.prev1;
		}
		return curStructA.prev1.toString();
	}
	
	/**
	 * Set parent pointer
	 * @param parent	parent Struct
	 */
	@Override
	public void set_parentStruct(Struct parent){
		this.parentStruct = parent;
	}
	
	@Override
	public Struct parentStruct(){		
		return this.parentStruct;
	}
	
	/**
	 * Set possessivePrev.
	 * @param prev	
	 */
	@Override
	public void set_possessivePrev(Struct prev){
		
	}
	
	@Override
	public Struct possessivePrev(){
		return null;
	}
	
	/**
	 * Sets the depth from root of the tree. Root has depth 0. *Not* intrinsic to the Struct,
	 * depends on the DFS path.
	 */
	@Override
	public void set_dfsDepth(int depth){
		this.depth = depth;
	}
	
	@Override
	public int dfsDepth(){
		return this.depth;
	}
	
	public int WLCommandStrVisitedCount(){
		return this.WLCommandStrVisitedCount;
	}
	
	public void clear_WLCommandStrVisitedCount(){
		this.WLCommandStrVisitedCount = 0;
	}
	
	public List<WLCommandWrapper> WLCommandWrapperList(){
		return this.WLCommandWrapperList;
	}
	
	public void clear_WLCommandWrapperList(){
		this.WLCommandWrapperList = null;
	}
	
	/**
	 * Retrieves corresponding WLCommandWrapper.
	 * @return
	 */
	public WLCommand WLCommandWrapper(WLCommandWrapper curCommandWrapper){		
		return this.WLCommandWrapperList.get(curCommandWrapper.listIndex()).WLCommand();
	}

	/**
	 * Create WLCommandWrapper from WLCommand.
	 * Add it to list of WLCommandWrappers.
	 * @return the created Wrapper.
	 */
	public WLCommandWrapper add_WLCommandWrapper(WLCommand curCommand){		
		if(this.WLCommandWrapperList == null){
			this.WLCommandWrapperList = new ArrayList<WLCommandWrapper>();
		}
		int listIndex = this.WLCommandWrapperList.size();
		WLCommandWrapper curCommandWrapper = new WLCommandWrapper(curCommand, listIndex);
		this.WLCommandWrapperList.add(curCommandWrapper);
		return curCommandWrapper;
	}
	
	/**
	 * 
	 * @param includeType
	 * @param curCommand Command that the returned String is built towards.
	 * curCommand is null if this struct should not be counted towards commandNumUnits.
	 * @return
	 */
	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand){
		//if(this.posteriorBuiltStruct != null) return "";
		
		//been built into one command already
		this.WLCommandStrVisitedCount++;
		if(this.WLCommandWrapperList != null){
			int wrapperListSz = WLCommandWrapperList.size();
			//wrapperListSz should be > 0, since list is created when first wrapper is added
			
			WLCommandWrapper curWrapper = WLCommandWrapperList.get(wrapperListSz - 1);
			if(curCommand != null){
				int commandNumUnits = WLCommand.commandNumUnits(curWrapper.WLCommand());
			 	WLCommand.increment_commandNumUnits(curCommand, commandNumUnits);
			}
			return curWrapper.WLCommandStr();			
		}		
		
		A name = this.prev1;
		return name instanceof String ? (String)name : this.simpleToString2(includeType, curCommand);
	}
	
	//auxilliary method for simpleToString and called inside StructH.simpleToString2
	public String simpleToString2(boolean includeType, WLCommand curCommand){
		//return "" if commandStr is not null (??)
		//if(this.WLCommandStr != null) return "";
		if(this.WLCommandWrapperList != null) return "";
		
		if(curCommand != null) WLCommand.increment_commandNumUnits(curCommand, this);
		
		//whether to wrap braces around the subcontent, to group terms together.
		//wrap if type is phrase, etc
		boolean wrapBraces = false;
		boolean inConj = false;
		String str = "";
		//tempStr to add to
		String tempStr = "";
		StringBuilder tempSB = new StringBuilder();
		
		//str += this.type.matches("conj_.*|disj_.*") ? this.type.split("_")[0] +  " " : "";		
		//also wrap braces around prev1 and prev2 or the conj/disj
		if(this.type.matches("conj_.*|disj_.*")){
			String toAppend = this.type.matches("conj_.*") ? "Conj" : "Disj";
			//str += this.type.split("_")[0] + " ";
			str += toAppend;
			inConj = true;
			//wrapBraces = true;
			tempStr += "[";
			tempSB.append("[");
		}
		
		if(this.type.matches("phrase|prep")){
			wrapBraces = true;		
			tempStr += "{";
			tempSB.append("{");
		}		
		
		if(this.prev1 != null){
			if(inConj){ tempStr += "{";
				tempSB.append("{");
			}
			//if(prev1 instanceof Struct && ((Struct) prev1).WLCommandStr() == null){
			if(prev1 instanceof Struct && ((Struct) prev1).WLCommandWrapperList() == null){
				String prev1Str = ((Struct) prev1).simpleToString2(includeType, curCommand);
				if(!prev1Str.matches("\\s*")){
					tempStr += prev1Str;
					tempSB.append(prev1Str);
				}
			}else if(prev1 instanceof String && !prev1.equals("")){
				//if(!type.matches("pre|partiby")){
				if(!type.matches("partiby")){
					tempStr += prev1;
					tempSB.append(prev1);
				}
			}
			if(inConj){ tempStr += "}";
				tempSB.append("}");
			}
		}
		
		if(prev2 != null){
			String prev2String = "";
			if(prev2 instanceof Struct && ((Struct) prev2).WLCommandWrapperList() == null){
				String prev2Str = ((Struct) prev2).simpleToString2(includeType, curCommand);
				if(!prev2Str.matches("\\s*")){
					if(!tempSB.toString().matches("\\s*")){ tempStr += ", ";
						tempSB.append(", ");
					}
					prev2String += prev2Str;
					
				}
			}else if(prev2 instanceof String && !((String)prev2).matches("\\s*")){			
				prev2String += ", " + prev2;			
			}
			tempStr += inConj ? "{" + prev2String + "}" : prev2String;
			tempSB.append(inConj ? "{" + prev2String + "}" : prev2String);
		}
		if(wrapBraces){ tempStr += "}";
			tempSB.append("}");
		}
		if(inConj){ tempStr += "]";
			tempSB.append("]");
		}
		//str += tempStr;
		str += tempSB;
		return str;
	}
	
	@Override
	public String contentStr(){		
		String contentStr = "";
		if(PREV1_TYPE != null && PREV1_TYPE.equals(NodeType.STR)){
			contentStr = (String) prev1;			
		}
		if(PREV2_TYPE != null && PREV2_TYPE.equals(NodeType.STR)){
			contentStr += " " + (String) prev2;
		}
		return contentStr;
	}
	
	/**
	 * Calling the applicable ParseTreeToVec with dynamic dispatch.
	 * To avoid casting, and to distribute the logic.
	 */
	@Override
	public void setContextVecEntry(int structParentIndex, int[] contextVec){
		ParseTreeToVec.setStructAContextVecEntry(this, structParentIndex, contextVec);
	}
	
	/**
	 * 
	 * @param prev1
	 * @param prev2
	 * @param type
	 * @param structList   pointer to list of Struct's containing this.
	 */
	/*
	public StructA(A prev1, B prev2, String type, double score, StructList structList){		
		this.prev1 = prev1;		
		this.prev2 = prev2;
		this.type = type; 
		this.structList = structList;
		this.score = score;
		this.numUnits = 1;
		//this.mxPathNodeList = new ArrayList<MatrixPathNode>();
	} */


	
	@Override
	public int numUnits(){
		return this.numUnits;
	}
	
	public Struct previousBuiltStruct(){
		return this.previousBuiltStruct;
	}
	
	public Struct posteriorBuiltStruct(){
		return this.posteriorBuiltStruct;
	}
	
	public void set_previousBuiltStruct(Struct previousBuiltStruct){
		this.previousBuiltStruct = previousBuiltStruct;
	}
	
	public void set_posteriorBuiltStruct(Struct posteriorBuiltStruct){
		this.posteriorBuiltStruct = posteriorBuiltStruct;
	}
	
	public Struct structToAppendCommandStr(){
		return this.structToAppendCommandStr;
	}
	
	public void set_structToAppendCommandStr(Struct structToAppendCommandStr){
		this.structToAppendCommandStr = structToAppendCommandStr;
	}

	
	@Override
	public void set_structList(StructList structList){
		this.structList = structList;
	}

	@Override
	public StructList StructList(){
		return this.structList;
	}
	
	@Override
	public double maxDownPathScore(){
		return this.maxDownPathScore;
	}
	
	/**
	 * This sets the max path score among any mxPathNode's that
	 * pass here. *down* score or total path score??
	 * 
	 * These scores will in turn be selected for the max score 
	 * inside the StructList this Struct is on.
	 * @param pathScore
	 */
	@Override
	public void set_maxDownPathScore(double pathScore){
		this.maxDownPathScore = pathScore;
	}
	
	@Override
	public double score(){
		return this.score;
	}
	
	public boolean has_child(){
		return false;
	}
	
	@Override
	public A prev1(){
		return this.prev1;		
	}
	
	@Override
	public B prev2(){
		return this.prev2;		
	}

	//use carefully: must know the declared type
	/////remove necessity to cast!
	@SuppressWarnings("unchecked")
	public void set_prev1(Object prev1){
		this.prev1 = (A)prev1;	
		if(prev1 instanceof Struct){
			this.PREV1_TYPE = ((Struct) prev1).isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;			
		}

	}
	
	//***this is terrible! Cannot just cast String
	@SuppressWarnings("unchecked")
	public void set_prev1(String prev1){	
		if(!(prev1 instanceof String)) 
			System.out.println("Cannot cast String to " + this.prev1.getClass() + "!");
		this.prev1 = (A)prev1;
	}
	
	@SuppressWarnings("unchecked")
	public void set_prev2(Object prev2){
		this.prev2 = (B)prev2;
		if(prev2 instanceof Struct){
			this.PREV2_TYPE = ((Struct) prev2).isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;			
		}
	}

	@SuppressWarnings("unchecked")
	public void set_prev2(String prev2){
		this.prev2 = (B)prev2;	
	}
	
	@Override
	public String type(){
		return this.type;		
	}

	@Override
	public void set_type(String type){
		this.type = type;		
	}
	
	/*public String type1(){
		return this.type1;		
	}

	public String type2(){
		return this.type2;		
	}*/

	@Override
	public void set_score(double score){
		this.score = score;
	}

	@Override
	public String toString(){
		String str = " type: " + this.type 				
				+ ", " + this.prev1;
		
		return str;
	}
	
	//used by present() in StructH; right now no need
	//to go deeper into prev1/prev2
	@Override
	public String present(String str){
		//str += this.type + "[";
		boolean showprev1 = true;
		if(this.type.matches("hyp") && this.prev1 instanceof String
				&& !((String)this.prev1).matches("for all|for every")){
			showprev1 = false;
		}
		
		//str += "[";
		//temporary string used to add to main str later. Use StringBuilder
		//in revamp!
		String tempStr = "";
		
		if(prev1 != null && !prev1.equals("")){
			if(prev1 instanceof Struct){
				tempStr = ((Struct) prev1).present(str);
				
			}else if(prev1 instanceof String && showprev1){
				if(!type.matches("pre|partiby")){
					tempStr += prev1;
				}
			}			
		}
		
		if(prev2 != null && !prev2.equals("")){
			if(prev2 instanceof Struct){
				tempStr = ((Struct) prev2).present(str + ", ");
			}else if(prev2 instanceof String){
				tempStr += ", " + prev2;
			}
		}
		
		if(!tempStr.matches("\\s*")){
			str += this.type.matches("conj_.*") ? this.type + "[" + tempStr + "]": "[" + tempStr + "]";
		}
		return str;
	}
	
	@Override
	public ArrayList<Struct> children() {
		return null;
	}

	@Override
	public List<String> childRelation() {
		return null;
	}

	@Override
	public void add_child(Struct child, String relation) {		
	}
	
}
