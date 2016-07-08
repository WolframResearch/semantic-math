package thmp;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * ParseStruct is a structure in the parse, can be 
 * STRUCT -- MathematicalStructure.
 * THM, HYP, DEF, PROP
 * Generic type T could be ParseStruct or Struct, ParseStruct
 * if overall head (eg MathThm), Struct if leaf (e.g. Restriction)
 * 
 * @author yihed
 */
public class ParseStruct {
	//enum
	private ParseStructType componentType;
	//map of structures such as qualifyingobject, quantifying variables, 
	//the keys are enums!
	private ListMultimap<ParseStructType, ParseStruct> parseStructMap;
	//map of Structs
	//**might not be used
	private ListMultimap<ParseStructType, Struct> structMap;
	//pointer to head Struct that leads this ParseStruct
	private Struct headStruct;
	
	//name of thm/hyp etc. Some ParseStructs don't need a name
	private String name;
	
	public ParseStruct(ParseStructType type, String name, Struct headStruct){
		this.componentType = type;
		this.name = name;
		this.parseStructMap = ArrayListMultimap.create();
		this.structMap = ArrayListMultimap.create();
		this.headStruct = headStruct;
		
		//how about just point to the same tree?
		//this.map = ArrayListMultimap.create(subParseTree);
	}
	
	public ParseStruct(ParseStructType type, Struct headStruct){
		this(type, null, headStruct);
	}
	
	public ParseStruct(Struct headStruct){
		this(null, null, headStruct);
	}
	
	public void addToStructMap(ParseStructType type, Struct struct){
		this.structMap.put(type, struct);
	}
	
	public void addToSubtree(ParseStructType type, ParseStruct subStruct){
		this.parseStructMap.put(type, subStruct);
	}
	
	public String name(){
		return this.name;
	}
	
	public Struct headStruct(){
		return this.headStruct;
	}
	
	public ParseStructType type(){
		return this.componentType;
	}

	public void set_type(ParseStructType type){
		this.componentType = type;
	}
	
}
