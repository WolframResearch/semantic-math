package thmp;

import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Enum for type of ParseStruct. can be STRUCT -- MathematicalStructure. THM,
 * HYP, DEF, PROP. Can also be "Restriction"
 * 
 * @author yihed
 *
 */

public enum ParseStructType {
	// thm and prop are prety much the same (in terms of structure)
	// NONE means shoud retain original type
	//difference between HYP and RES??
	HYP, PPT, OBJ, RES, STM, NONE;
	
	/**
	 * Map of which String maps to which ParseStructType enum.
	 * Is immutable map.
	 */
	private static final ImmutableListMultimap<String, ParseStructType> StringParseStructTypeMap;
	
	static{
		//type should not be determined based just on head type
		//construct a multimap, and take its inverse
		ImmutableListMultimap.Builder<ParseStructType, String> builder =
				new ImmutableListMultimap.Builder<ParseStructType, String>();
		builder.putAll(HYP, Arrays.asList("letbe", "hypo", "partient", "if", "If"));
		builder.putAll(RES, Arrays.asList("where", "assuming"));
		builder.putAll(OBJ, Arrays.asList("ent", "MathObj"));
		builder.putAll(RES, Arrays.asList("Cond"));
		
		StringParseStructTypeMap = builder.build().inverse();		
	}
	
	/**
	 * Get ParseStructType based on typeStr, corresponding
	 * to type of Struct.
	 * @param typeStr
	 * @return
	 */
	public static ParseStructType getType(String typeStr) {
		
		ParseStructType type;		
		if(StringParseStructTypeMap.containsKey(typeStr)){
			 type = StringParseStructTypeMap.get(typeStr).get(0);
		}else{
			type = STM;
		}
		
		return type;
	}
	
	/**
	 * Get ParseStructType from on nameStr, 
	 * corresponding to name of Struct.
	 * @param typeStr
	 * @return
	 */
	public static ParseStructType getTypeFromName(String nameStr) {
		ParseStructType type;
		if(StringParseStructTypeMap.containsKey(nameStr)){
			 type = StringParseStructTypeMap.get(nameStr).get(0);
		}else{
			type = STM;
		}
		
		return type;
	}
}
