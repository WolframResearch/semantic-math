package thmp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.ParseToWLTree.WLCommandWrapper;

/**
 * ParseStruct is a structure in the parse, can be 
 * STRUCT -- MathematicalStructure.
 * THM, HYP, DEF, PROP
 * 
 * @author yihed
 */
public class ParseStruct {
	//enum for type, e.g. "HYP", "STM", etc.
	//private ParseStructType componentType;
	//map of structures such as qualifying object, quantifying variables. 
	//The keys are enums. Should preserve keys as well!
	private List<ParseStruct> childrenParseStructList;
	//map of Structs, i.e. heads of statements. 
	//pointer to head Struct that leads this ParseStruct
	//private Struct headStruct;
	//structs on this layer of the tree.
	//private List<Struct> structList;
	private Multimap<ParseStructType, WLCommandWrapper> wrapperMMap;
	
	//whether this ParseStruct is currently in hypothetical mode, don't create 
	//sub ParseStruct's once in hyp mode.
	//private boolean inHyp;
	
	String WLCommandStr;
	
	/**
	 * @return the structList, list of structs associated with this 
	 * layer.
	 */
	public Multimap<ParseStructType, WLCommandWrapper> getStructMMap() {
		return wrapperMMap;
	}

	/*public boolean inHyp(){
		return inHyp;
	}
	
	public void setInHyp(boolean inHyp){
		this.inHyp = inHyp;
	}*/
	
	/**
	 * Adds additional struct to the structList.
	 */
	public void addParseStructWrapper(ParseStructType type, WLCommandWrapper wrapper) {
		this.wrapperMMap.put(type, wrapper);
	}
	
	/**
	 * Adds additional struct to the structList.
	 */
	public void addParseStructWrapper(Multimap<ParseStructType, WLCommandWrapper> map) {
		
		this.wrapperMMap.putAll(map);
	}
	
	/**
	 * @return the wLCommandStr
	 */
	public String getWLCommandStr() {
		return WLCommandStr;
	}

	/**
	 * @param wLCommandStr the wLCommandStr to set
	 */
	public void setWLCommandStr(String wLCommandStr) {
		WLCommandStr = wLCommandStr;
	}

	//the parent ParseStruct
	private ParseStruct parentParseStruct;
	
	public ParseStruct(){
		//this.componentType = type;
		this.childrenParseStructList = new ArrayList<ParseStruct>();
		this.wrapperMMap = ArrayListMultimap.create();
		//this.wrapperMMap.put(type, headStruct);		
		//how about just point to the same tree?
		//this.map = ArrayListMultimap.create(subParseTree);
	}
	
	/**
	 * @param type
	 * @param subStruct	Struct to be added to this ParseStruct 
	 */
	public void addToSubtree(ParseStruct subParseStruct){
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		this.childrenParseStructList.add(subParseStruct);
	}
	
	public ParseStruct parentParseStruct(){
		return this.parentParseStruct;
	}
	
	public void set_parentParseStruct(ParseStruct parent){
		this.parentParseStruct = parent;
	}
	
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		return this.toString(sb);
	}
	
	private String toString(StringBuilder sb){
		
		Collection<Map.Entry<ParseStructType, WLCommandWrapper>> wrapperMMapEntries = 
				wrapperMMap.entries();
		int i = wrapperMMapEntries.size();
		
		for(Map.Entry<ParseStructType, WLCommandWrapper> entry : wrapperMMapEntries){
			if(i > 1 || !childrenParseStructList.isEmpty()){
				sb.append(entry.getKey() + " :> " + entry.getValue().WLCommandStr() + ", ");
			}else{
				sb.append(entry.getKey() + " :> " + entry.getValue().WLCommandStr());
			}
			i--;
		}
		
		//Collection<Map.Entry<ParseStructType, ParseStruct>> parseStructMMapEntries = 
			//	childrenParseStructList.entries();
		i = childrenParseStructList.size();
		
		//recursively call toString
		for(ParseStruct childParseStruct : childrenParseStructList){
			if(i > 1){
				sb.append(" {" + childParseStruct + "};");
			}else{
				sb.append(" {" + childParseStruct + "}");
			}
			i--;
			//throw new IllegalStateException(entry.getValue().toString());
		}
		
		return sb.toString();
	}
	
}
