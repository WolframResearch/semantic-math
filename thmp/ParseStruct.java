package thmp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * ParseStruct is a structure in the parse, can be 
 * STRUCT -- MathematicalStructure.
 * THM, HYP, DEF, PROP
 * 
 * @author yihed
 */
public class ParseStruct {
	//enum for type, e.g. "HYP", "STM", etc.
	private ParseStructType componentType;
	//map of structures such as qualifying object, quantifying variables. 
	//The keys are enums. Should preserve keys as well!
	private ListMultimap<ParseStructType, ParseStruct> parseStructMap;
	//map of Structs, i.e. heads of statements. 
	//pointer to head Struct that leads this ParseStruct
	//private Struct headStruct;
	//structs on this layer of the tree.
	private List<Struct> structList;

	String WLCommandStr;
	
	/**
	 * @return the structList, list of structs associated with this 
	 * layer.
	 */
	public List<Struct> getStructList() {
		return structList;
	}

	/**
	 * Adds additional struct to the structList.
	 */
	public void addStruct(Struct newStruct) {
		this.structList.add(newStruct);
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
	
	public ParseStruct(ParseStructType type, Struct headStruct){
		this.componentType = type;
		this.parseStructMap = ArrayListMultimap.create();
		this.structList = new ArrayList<Struct>();
		this.structList.add(headStruct);		
		//how about just point to the same tree?
		//this.map = ArrayListMultimap.create(subParseTree);
	}
	
	/**
	 * @param type
	 * @param subStruct	Struct to be added to this ParseStruct 
	 */
	public void addToSubtree(ParseStructType type, ParseStruct subParseStruct){
		this.parseStructMap.put(type, subParseStruct);
	}
	
	public ParseStruct parentParseStruct(){
		return this.parentParseStruct;
	}
	
	public void set_parentParseStruct(ParseStruct parent){
		this.parentParseStruct = parent;
	}
	
	
	public ParseStructType type(){
		return this.componentType;
	}

	public void set_type(ParseStructType type){
		this.componentType = type;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		return this.toString(sb);
	}
	
	private String toString(StringBuilder sb){
		//recursively call toString
		for(Map.Entry<ParseStructType, ParseStruct> entry : parseStructMap.entries()){
			sb.append(entry.getKey().toString() + ". ");
		}
		
		for(Struct struct : structList){
			sb.append(struct + "; ");
		}
		
		return sb.toString();
	}
	
}
