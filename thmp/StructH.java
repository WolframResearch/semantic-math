package thmp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import static thmp.ParseToWLTree.WLCommandWrapper;

public class StructH<H> extends Struct{

	private HashMap<String, String> struct; //hashmap
	//ent (entity) is only structure that uses hashmap
	private String type; //ent, adj, etc
	private String WLCommandStr;
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
	private Struct posteriorBuiltStruct;
	//the head Struct (to append to) of a WLCommand this Struct currently belongs to.
	//Not intrinsic to this Struct!
	private Struct structToAppendCommandStr;
	//parentStruct is *not* unique! Depends on which DFS path we take.
	private Struct parentStruct;
	private boolean hasChild = false;
	private ArrayList<Struct> children; 
	//relation to child, eg "of," "enjoyed"
	private ArrayList<String> childRelation;	
	private double score;
	private double DOWNPATHSCOREDEFAULT = 1;
	private double maxDownPathScore = DOWNPATHSCOREDEFAULT;
	private StructList structList;
	private int NUMUNITS = 1;
	
	//parent
	//private Struct parent;
	
	public StructH(HashMap<String, String> struct, String type){
	
		this.struct = struct;
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelation = new ArrayList<String>();
		this.score = 1;
	}
	
	public StructH(HashMap<String, String> struct, String type, 
			StructList structList, double downPathScore){
		this.maxDownPathScore = downPathScore;
		this.struct = struct;
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelation = new ArrayList<String>();
		this.score = 1;
		this.structList = structList;
	}

	//when is this used??
	public StructH(String type){
		
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelation = new ArrayList<String>();
		this.score = 1;
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
	
	public void set_struct(HashMap<String, String> struct){
		this.struct = struct;
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
	
	//make deep copy, struct and children children are copied
	@Override
	public StructH<H> copy(){
		HashMap<String, String> structCopy = new HashMap<String, String>(this.struct);
		StructH<H> newStructH = new StructH<H>(structCopy, this.type, this.structList,
				this.maxDownPathScore);
		
		for(int i = 0; i < this.children.size(); i++){
			newStructH.add_child(this.children.get(i), this.childRelation.get(i));
		}
		
		return newStructH;
	}
	
	public void add_previous(Struct prev){
		hasChild = true;
		children.add(prev);
		//if no relation specified
		childRelation.add("");
	}

	@Override
	public void add_child(Struct child, String relation){
		hasChild = true;
		children.add(child);
		childRelation.add(relation);
	}
	
	public boolean has_child(){
		return hasChild;		
	}
	
	@Override
	public ArrayList<Struct> children(){
		return children;		
	}
	
	@Override
	public ArrayList<String> childRelation(){
		return childRelation;		
	}
	
	@Override
	public HashMap<String, String> struct(){		
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
		String str = this.type;
		str += this.struct; //struct is hashmap for structH
		
		return str;
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
		//System.out.println("CALLED BY: " + this);
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
	 * @return
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
	 * Simple toString to return the bare minimum to present this Struct.
	 * To be used in ParseToWLTree.
	 * @param includeType	Whether to include the type, eg "MathObj"
	 * @return
	 */
	@Override
	public String simpleToString(boolean includeType){
		//if(this.posteriorBuiltStruct != null) return "";
		this.WLCommandStrVisitedCount++;
		// instead of checking WLCommandStr, check if wrapperList is null
		// ie if any command has been assigned to this Struct yet. If yes,
		// get the last one (might want the one with highest commandsWithOtherHead
		// later.
		/*if(this.WLCommandStr != null){
			return this.WLCommandStr;
		} */
		if(this.WLCommandWrapperList != null){
			int wrapperListSz = WLCommandWrapperList.size();
			//wrapperListSz should be > 0, since list is created when first wrapper is added
			return WLCommandWrapperList.get(wrapperListSz - 1).WLCommandStr();			
		}		
		//String name = this.struct.get("name");
		//return name == null ? this.type : name;
		return this.simpleToString2(includeType);
	}
	
	//auxilliary method for simpleToString and StructA.simpleToString
	public String simpleToString2(boolean includeType){
		String str = "";
		if(includeType){ 			
			str += this.type.equals("ent") ? "MathObj" : this.type;
			str += "{";
		}
		str += append_name_pptStr();
		
		//iterate through children		
		int childrenSize = children.size();
		for(int i = 0; i < childrenSize; i++){			
			Struct child = children.get(i);
			if(child.WLCommandWrapperList() != null)
				continue;
			//str += ", ";
			//str += childRelation.get(i) + " ";	
			String childStr = child.simpleToString2(includeType);
			//str += childStr;	
			if(!childStr.matches("\\s*")){
				str += ", " + childStr;				
			}
		}		
		if(includeType) str += "}";

		return str;
	}
	
	/**
	 * Append name, ppt, called, tex info to the String passed in.
	 * @param str String to be appended to
	 */
	public String append_name_pptStr(){
		Iterator<Entry<String, String>> structIter = struct.entrySet().iterator();
		String name = "", called = "", ppt = "", tex = "";
		
		while(structIter.hasNext()){
			Entry<String, String> entry = structIter.next();
			if(entry.getValue().matches("ppt") ){
				ppt += entry.getKey() + ", ";
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
		
		name = tex.length() > 0 ? name + ", ": name;
		tex = called.length() > 0 ? tex + ", ": tex;
		called = !(ppt.length() == 0) ? called + ", " : called;
		ppt = ppt.length() > 2 ? ppt.substring(0, ppt.length() - 2) : ppt;		
		
		return name + tex + called + ppt;
	}
	
	//similar to toString(). Presents StructH as a String
	@Override
	public String present(String str){
		str += this.type.equals("ent") ? "MathObj" : this.type;
		str += "{";
		
		str += append_name_pptStr();
		
		if(children.size() > 0) str += ", ";
		
		//iterate through children		
		int childrenSize = children.size();
		for(int i = 0; i < childrenSize; i++){
			str += childRelation.get(i) + " ";
			Struct child = children.get(i);
			str = child.present(str);	
			if(i < childrenSize - 1)
				str += ", ";
		}
		
		str += "}";

		return str;
	}

	@Override
	public void set_prev1(String str) {
		// TODO 		
	}

	@Override
	public Object prev1() {
		// TODO 
		return null;
	}

	@Override
	public Object prev2() {
		// TODO 
		return null;
	}
	
}
