package thmp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import thmp.ParseToWLTree.WLCommandWrapper;
import thmp.Struct.NodeType;

/**
 * 
 * *Need* to modify copy() when any field is added to StructH class.
 * @author yihed
 * @param <H>
 */
public class StructH<H> extends Struct{

	private Map<String, String> struct; //hashmap
	//ent (entity) is only structure that uses hashmap
	//Primary part of speech, ent, adj, etc. 
	private String type; 
	//*additional* part of speech
	private volatile Set<String> extraPosSet;
	
	//the number of times this WLCommandStr has been visited.
	//To not repeat, print only when this is even
	private int WLCommandStrVisitedCount;
	//List of WLCommandWrapper associated with this Struct, should have 
	//corresponding WLCommandStr. Each Wrapper contains its index in list.
	//private WLCommand WLCommand;
	private List<WLCommandWrapper> WLCommandWrapperList;
	
	//pointer to the head of a previously built Struct that already
	//contains this Struct, so no need to build this Struct again into the current 
	//WLCommand in build(), remember to reset to null after iterating through
	private Struct previousBuiltStruct;
	//don't think the posterior one is used.
	private Struct posteriorBuiltStruct;
	//the head Struct (to append to) of a WLCommand this Struct currently belongs to.
	//Not intrinsic to this Struct!
	private Struct structToAppendCommandStr;
	//parentStruct is *not* unique! Depends on which DFS path we take.
	private Struct parentStruct;
	//Depth from root of the tree. Root has depth 0. *Not* intrinsic to the Struct, 
	//depends on the DFS path.
	private int depth;
	private boolean hasChild = false;
	private List<Struct> children; 
	//relation to child, eg "of," "which is", "over", 
	//as in "field over Q."
	private List<ChildRelation> childRelationList;	
	private double score;
	private double DOWNPATHSCOREDEFAULT = 1;
	private double maxDownPathScore = DOWNPATHSCOREDEFAULT;
	private StructList structList;
	private int NUMUNITS = 1;
	//Struct that's the owner of this structH with a possesive term relation
	//eg related by its/their.
	private Struct possessivePrev;
	//set of properties, to avoid iterating through the HashMap struct each time 
	//properties are needed (since "ppt" are values, not keys, in struct). This set caches the properties.
	//Should not have used String "ppt" to record properties in struct!
	private final Set<String> propertySet = new HashSet<String>();
	//use this variable to avoid race conditions, since propertySet can be added to by multiple methods.
	private volatile boolean isPropertySetEmpty = true;
	
	//whether this structH arises from a latex expression, e.g. $x > 0$.
	//Used to convert ent into assert if necessary. 
	private boolean isLatexStruct;
	
	private WLCommand commandBuilt;
	
	//parent
	//private Struct parent;
	
	public StructH(Map<String, String> struct, String type){
	
		this.struct = struct;
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelationList = new ArrayList<ChildRelation>();
		this.score = 1;
	}
	
	public StructH(Map<String, String> struct, String type, 
			StructList structList, double downPathScore){
		this.maxDownPathScore = downPathScore;
		this.struct = struct;
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelationList = new ArrayList<ChildRelation>();
		this.score = 1;
		this.structList = structList;
	}

	public StructH(String type){		
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelationList = new ArrayList<ChildRelation>();
		this.score = 1;
	}
	
	
	/**
	 * Set parent pointer
	 * @param parent  parent Struct
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
		this.possessivePrev = prev;
	}
	
	@Override
	public Struct possessivePrev(){
		return this.possessivePrev;
	}
	
	@Override
	public void set_dfsDepth(int depth){
		this.depth = depth;
	}
	
	@Override
	public int dfsDepth(){
		return this.depth;
	}
	
	public NodeType prev1NodeType(){
		return NodeType.NONE;
	}
	
	public NodeType prev2NodeType(){
		return NodeType.NONE;
	}
	
	/*
	@Override
	public void append_WLCommandStr(String WLCommandStr){
		this.WLCommandStr = this.WLCommandStr == null ? "" : this.WLCommandStr;
		this.WLCommandStr += " " + WLCommandStr;
	}
	
	@Override
	public void clear_WLCommandStr(){
		this.WLCommandStr = null;
	}
	
	@Override
	public String WLCommandStr(){
		return this.WLCommandStr;
	}
	*/
	
	/**
	 * @return the isLatexStruct
	 */
	@Override
	public boolean isLatexStruct() {
		return isLatexStruct;
	}

	/**
	 * @param isLatexStruct the isLatexStruct to set
	 */
	public void setLatexStructToTrue() {
		this.isLatexStruct = true;
	}

	public void set_struct(HashMap<String, String> struct){
		this.struct = struct;
	}

	@Override
	public void set_structList(StructList structList){
		this.structList = structList;
	}
	
	@Override
	public StructList get_StructList(){
		return this.structList;
	}
	
	/**
	 * List of additional pos, e.g. "prime" has pos adj, but is 
	 * often used as an "ent". "type()" contains primary pos.
	 * @return
	 */
	@Override
	public Set<String> extraPosSet(){
		return this.extraPosSet;
	}
	
	/**
	 * Add additional parts of speech to this struct.
	 * (Beyond the first/default pos.)
	 */
	@Override
	public void addExtraPos(String pos){
		//System.out.println("StructH addExtraPos" + this + Arrays.toString(Thread.currentThread().getStackTrace()));
		//Lazy initialization with double-check locking.
		if(extraPosSet == null){
			synchronized(this){
				if(extraPosSet == null){
					extraPosSet = new HashSet<String>();
				}
			}
		}
		extraPosSet.add(pos);		
	}
	
	@Override
	public void set_maxDownPathScore(double pathScore){
		this.maxDownPathScore = pathScore;
	}
	
	@Override
	public double maxDownPathScore(){
		return this.maxDownPathScore;
	}
	
	@Override
	public double score(){
		return this.score;
	}
	
	@Override
	public void set_score(double score){
		this.score = score;
	}
	
	@Override
	public int numUnits(){
		return NUMUNITS;
	}
	
	/**
	 * Make deep copy, struct and children children are copied.
	 * Parent of newly copied structs is previous Struct's parent.
	 * *Need* to modify this when any field is added to StructH class.
	 * @return
	 */
	@Override
	public StructH<H> copy(){
		
		Map<String, String> structCopy = new HashMap<String, String>(this.struct);
		StructH<H> newStructH = new StructH<H>(structCopy, this.type, this.structList,
				this.maxDownPathScore);
		
		for(int i = 0; i < this.children.size(); i++){
			Struct child = children.get(i);
			newStructH.add_child(child, this.childRelationList.get(i));
			//copy should not modify the state of the original struct, change name.
			child.set_parentStruct(newStructH);
		}
		
		newStructH.set_parentStruct(parentStruct);
		
		if(this.isLatexStruct){
			newStructH.setLatexStructToTrue();
		}
		return newStructH;
	}
	
	public void add_previous(Struct prev){
		hasChild = true;
		children.add(prev);
		//if no relation specified
		childRelationList.add(new ChildRelation(""));
	}

	@Override
	public void add_child(Struct child, ChildRelation relation){
		hasChild = true;
		children.add(child);
		childRelationList.add(relation);
		child.set_parentStruct(this);
	}
	
	public boolean has_child(){
		return hasChild;		
	}
	
	@Override
	public List<Struct> children(){
		return children;		
	}
	
	@Override
	public List<ChildRelation> childRelationList(){
		return childRelationList;		
	}
	
	@Override
	public Map<String, String> struct(){		
		return this.struct;		
	}
	
	@Override
	public String type(){
		return this.type;		
	}
	
	@Override
	public void set_type(String type){
		this.type = type;		
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("[" + this.type);
		//String str = "[" + this.type;
		sb.append(this.struct);
		//str += this.struct; //struct is hashmap for structH
		/*for(Map.Entry<String, String> e: this.struct.entrySet()){
			str += e;
		}*/
		if(this.possessivePrev != null){
			sb.append("possessivePrev: ");
			sb.append(possessivePrev.type());
			//str += "possessivePrev: " + possessivePrev.type();
		}
		/*if(children.size() > 0){
			sb.append(" children: ");
			sb.append(children);
		}*/
		//str+= " children.size(): ";
		//str += children.size();
		return sb.append(']').toString();
	}
	
	public int WLCommandStrVisitedCount(){
		return this.WLCommandStrVisitedCount;
	}
	
	public void clear_WLCommandStrVisitedCount(){
		this.WLCommandStrVisitedCount = 0;
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
	
	/**
	 * Set the WLCommandWrapper.
	 * @param newCommand
	 */
	/*public void set_WLCommandWrapper(WLCommandWrapper newCommandWrapper){
		this.WLCommandWrapper = newCommandWrapper;
	} */
	
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
	 * Consider making moving this to parent class Struct.java.
	 * @return
	 */
	public WLCommandWrapper add_WLCommandWrapper(WLCommand curCommand){		
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		if(this.WLCommandWrapperList == null){
			this.WLCommandWrapperList = new ArrayList<WLCommandWrapper>();
		}
		int listIndex = this.WLCommandWrapperList.size();
		WLCommandWrapper curCommandWrapper = new WLCommandWrapper(curCommand, listIndex);
		//add wrapper reference to curCommand
		curCommand.setCommandWrapper(curCommandWrapper);
		this.WLCommandWrapperList.add(curCommandWrapper);
		return curCommandWrapper;
	}
	
	/**
	 * Simple toString to return the bare minimum to present this Struct.
	 * To be used in ParseToWLTree.
	 * @param includeType	Whether to include the type, eg "MathObj"
	 * @param curCommand current WLCommand this Struct is showing up in
	 * @return
	 */
	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand){
		//if(this.posteriorBuiltStruct != null) return "";
		//this.WLCommandStrVisitedCount++;
		// instead of checking WLCommandStr, check if wrapperList is null
		// ie if any command has been assigned to this Struct yet. If yes,
		// get the last one (might want the one with highest commandsWithOtherHead
		// later).
		/*if(this.WLCommandStr != null){
			return this.WLCommandStr;
		} */		
		if(this.WLCommandWrapperList != null){
			int wrapperListSz = WLCommandWrapperList.size();
			//wrapperListSz should be > 0, since list is created when first wrapper is added
			WLCommandWrapper curWrapper = WLCommandWrapperList.get(wrapperListSz - 1);
			if(curCommand != null){
				int commandNumUnits = WLCommand.commandNumUnits(curWrapper.WLCommand());
			 	WLCommand.increment_commandNumUnits(curCommand, commandNumUnits);
			}
			System.out.println("^^^curWrapper: " + curWrapper);
			
			//been built into one command already
			this.WLCommandStrVisitedCount++;
			return curWrapper.WLCommandStr();			
		}		
		//String name = this.struct.get("name");
		//return name == null ? this.type : name;
		return this.simpleToString2(includeType, curCommand);
	}
	
	/**
	 * Auxilliary method for simpleToString and StructA.simpleToString.
	 */
	@Override
	public String simpleToString2(boolean includeType, WLCommand curCommand){
		
		if(curCommand != null) {
			WLCommand.increment_commandNumUnits(curCommand, this);
		}
		//been built into one command already
		//this.WLCommandStrVisitedCount++;
		if(null == this.commandBuilt){
			this.commandBuilt = curCommand;
			this.WLCommandStrVisitedCount++;
		}else if(!curCommand.equals(this.commandBuilt)){
			this.WLCommandStrVisitedCount++;
		}
		
		//String str = "";
		StringBuilder sb = new StringBuilder();
		
		if(includeType){
			
			sb.append(this.type.equals("ent") ? "Math" : this.type);
			sb.append("[");
			//str += this.type.equals("ent") ? "Math" : this.type;
			//str += "[\"";
		}
		//str += "{";
		//str += append_name_pptStr();
		/*if(includeType){
			sb.append("\"");
		}*/
		
		Iterator<String> pptStrListIter = get_name_pptStr_List().iterator();
		
		if(pptStrListIter.hasNext()){			
			sb.append("\"Type\"->");
		}
		
		while(pptStrListIter.hasNext()){
			String nextStr = pptStrListIter.next();
			if(pptStrListIter.hasNext()){
				sb.append("\"" + nextStr + "\", ");
			}else{
				sb.append("\"" + nextStr + "\"");
			}
		}
		
		//sb.append(append_name_pptStr());
		
		/*if(includeType){ 
			//str += "\"]";
			sb.append("\"");
		}*/
		
		//iterate through children		
		int childrenSize = children.size();
		if(childrenSize > 0){
			
			StringBuilder childSb = new StringBuilder();
			for(int i = 0; i < childrenSize; i++){	
				Struct child = children.get(i);
				String childRelationStr = childRelationList.get(i).childRelation;
				/*if(child.WLCommandWrapperList() != null){
					continue;
				}*/
				//str += ", ";
				//str += childRelation.get(i) + " ";	
				//System.out.println("^^^cur child: " + child);
				
				String childStr = child.simpleToString(includeType, curCommand);
				//str += childStr;	
				if(!childStr.matches("\\s*")){
					//only append curChidRelation if child is a StructH, to avoid
					//including the relation twice, eg in case child is of type "prep"
					//if this child has been used in another component of the same command.
					if(!child.usedInOtherCommandComponent()){
						//don't want "symb", e.g. $G$ with $H$ prime. 
						childRelationStr = (child.isStructA() && !child.type().equals("symb") 
								//e.g. "field which is perfect", don't want "which"								
								)
								? "" : childRelationStr;
						//str += ", " + curChildRelation + childStr;
						//System.out.println("\n **^^^*** childRelation" + childRelationStr);
						if(childRelationStr.equals("")){
							childSb.append("{" + childStr + "}");
							if(i < childrenSize-1){
								childSb.append(", ");
							}
						}else{
							childSb.append("{\"" + childRelationStr + "\", " + childStr + "}");
							if(i < childrenSize-1){
								childSb.append(", ");
							}
						}
					}
				}
			}

			if(0 < childSb.length()){
				sb.append(", \"Qualifiers\" -> ");
				sb.append(childSb);
			}
			
		}
		if(includeType){ 
			//str += "\"]";
			sb.append("]");
		}
		//str += "}";
		
		return sb.toString();
	}
	
	/*@Override
	public boolean commandVisited(){
		return true;
	}*/
	
	@Override
	public void clear_commandBuilt(){
		this.commandBuilt = null;
	}
	
	/**
	 * 
	 * @return set of properties of this StructH.
	 */
	public Set<String> getPropertySet(){
		//seek out the properties in struct
		//thread-safe with volatile boolean isPropertySetEmpty. But could add to map multiple times
		//if called by multiple threads, could be little wasteful but no race condition.
		if(isPropertySetEmpty){
			for(Map.Entry<String, String> entry : struct.entrySet()){
				if(entry.getValue().equals("ppt")){
					propertySet.add(entry.getKey());
				}
			}
		}		
		isPropertySetEmpty = false;
		return propertySet;		
	}
	
	/**
	 * Retrieve name, ppt, called, tex info.
	 * @return List of name, ppt, strings, etc.
	 */
	private List<String> get_name_pptStr_List(){
		
		List<String> namePptStrList = new ArrayList<String>();
		Iterator<Entry<String, String>> structIter = struct.entrySet().iterator();
		String name = "", called = "", ppt = "", tex = "";
		//boolean addToPropertySet = propertySet.isEmpty();
		
		while(structIter.hasNext()){
			Entry<String, String> entry = structIter.next();
			
			if(entry.getValue().matches("ppt") ){
				String newPpt = entry.getKey();
				ppt += newPpt + ", ";
				if(isPropertySetEmpty){
					propertySet.add(newPpt);
				}
			}
			else if(entry.getKey().matches("name") ){
				name = entry.getValue();
			}
			else if(entry.getKey().matches("called") ){
				called = entry.getValue();
			}
			else if(entry.getKey().matches("tex") ){
				tex = entry.getValue();
			}
		}		
		
		isPropertySetEmpty = false;
		
		if(!name.equals("")) namePptStrList.add(name);
		if(!tex.equals("")) namePptStrList.add(tex);
		if(!called.equals("")) namePptStrList.add(called);
		if(!ppt.equals("")){ 
			String pptStr = ppt;
			pptStr = pptStr.length() > 2 ? pptStr.substring(0, pptStr.length() - 2) : pptStr;
			namePptStrList.add(pptStr);
		}
		return namePptStrList;
	}
	
	/**
	 * Retrieve name, ppt, called, tex info.
	 */
	public String append_name_pptStr(){
		
		List<String> namePptStrList = get_name_pptStr_List();
		int namePptStrListLen = namePptStrList.size();
		StringBuilder sb = new StringBuilder();
				
		for(int i = 0; i < namePptStrListLen-1; i++){
			String namePptStr = namePptStrList.get(i);
			String nextStr = namePptStrList.get(i+1);
			
			if(!nextStr.equals("")){
				sb.append(namePptStr + ", ");
			}else{
				sb.append(namePptStr);
			}			
		}
		
		String pptStr = namePptStrList.get(namePptStrListLen-1);
		
		sb.append(pptStr.length() > 2 ? pptStr.substring(0, pptStr.length() - 2) : pptStr);
		/*
		name = tex.length() > 0 ? name + ", ": name;
		tex = called.length() > 0 ? tex + ", ": tex;
		called = !(ppt.length() == 0) ? called + ", " : called;
		ppt = ppt.length() > 2 ? ppt.substring(0, ppt.length() - 2) : ppt;			

		for(String str : ){
			sb.append(str);
		}*/
		return sb.toString();
	}
	
	//similar to toString(). Presents StructH as a String
	@Override
	public String present(String str){
		str += this.type.equals("ent") ? "Math" : this.type;
		str += "[";
		
		str += append_name_pptStr();
		
		if(children.size() > 0) str += ", ";
		
		//iterate through children		
		int childrenSize = children.size();
		for(int i = 0; i < childrenSize; i++){
			str += childRelationList.get(i) + " ";
			Struct child = children.get(i);
			str = child.present(str);	
			if(i < childrenSize - 1)
				str += ", ";
		}
		
		str += "]";

		return str;
	}

	@Override
	public void set_prev1(String str) {
			
	}

	@Override
	public Object prev1() {
		
		return null;
	}

	@Override
	public Object prev2() {
		
		return null;
	}
	
	@Override
	public String contentStr(){
		String str = struct.get("name");
		String contentStr = str == null ? "" : str; 
		return contentStr;		
	}
	
	@Override
	public String nameStr(){		
		return contentStr();
	}
	
	/**
	 * Calling the applicable ParseTreeToVec with dynamic dispatch.
	 * To avoid casting, and to distribute the logic.
	 */
	@Override
	public void setContextVecEntry(int structParentIndex, int[] contextVec){
		ParseTreeToVec.setStructHContextVecEntry(this, structParentIndex, contextVec);
	}
	
}
