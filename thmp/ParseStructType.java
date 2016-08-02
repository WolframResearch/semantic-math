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
	HYP, PPT, OBJ, RES, STM, NONE;
	
	/**
	 * Map of which String maps to which ParseStructType enum.
	 * Is immutable map.
	 */
	private static final ImmutableListMultimap<String, ParseStructType> f;
	
	static{
		//construct a multimap, and take its inverse
		ImmutableListMultimap.Builder<ParseStructType, String> builder =
				new ImmutableListMultimap.Builder<ParseStructType, String>();
		builder.putAll(HYP, Arrays.asList("letbe", "hypo"));
		builder.putAll(RES, Arrays.asList("where", "assuming"));
		builder.putAll(OBJ, Arrays.asList("ent"));
		
		f = builder.build().inverse();		
	}

	public static ParseStructType getType(String typeStr) {
		ParseStructType type;
		if(f.containsKey(typeStr)){
			 type = f.get(typeStr).get(0);
		}else{
			type = STM;
		}
		
		return type;
	}
}
