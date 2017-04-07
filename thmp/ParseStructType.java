package thmp;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Enum for type of ParseStruct. can be STRUCT -- MathematicalStructure. THM,
 * HYP, DEF, PROP. Can also be "Restriction"
 * 
 * @author yihed
 */

public enum ParseStructType {
	// thm and prop are prety much the same (in terms of structure)
	// NONE means shoud retain original type.
	HYP, HYP_iff, PPT, OBJ, STM, NONE;
	
	/**
	 * Map of which String maps to which ParseStructType enum.
	 * Is immutable map.
	 */
	private static final ImmutableListMultimap<String, ParseStructType> StringParseStructTypeMap;
	
	private static final Pattern CONJ_DISJ_PATTERN = Pattern.compile("(?:conj|disj)_(.+)");
	static{
		//type should not be determined based just on head type
		//construct a multimap, and take its inverse
		//The string value can be either pos (part of speech) or word string
		//should be as specific as possible. E.g. use "for all" rather than "hyp"
		ImmutableListMultimap.Builder<ParseStructType, String> builder =
				new ImmutableListMultimap.Builder<ParseStructType, String>();		
		builder.putAll(HYP, Arrays.asList("letbe", "let", "hypo", "partient", "if", "If", "where", "assuming", "assume", "Cond"));
		builder.putAll(HYP_iff, Arrays.asList("if and only if", "iff", "Iff"));
		builder.putAll(OBJ, Arrays.asList("ent", "MathObj"));
		builder.putAll(STM, Arrays.asList("then"));
		
		StringParseStructTypeMap = builder.build().inverse();	
	}
	
	/**
	 * Whether the type is a hypothesis type.
	 * @return
	 */
	public boolean isHypType(){
		if(this.equals(HYP) || this.equals(HYP_iff)){
			return true;
		}
		return false;
	}
	
	/**
	 * Get ParseStructType based on typeStr, corresponding
	 * to type of Struct.
	 * @param struct The current Struct we are getting the type of
	 * @return
	 */
	public static ParseStructType getType(Struct struct) {
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		String typeStr = struct.type();
		Matcher matcher;
		if((matcher=CONJ_DISJ_PATTERN.matcher(typeStr)).matches()){
			typeStr = matcher.group(1);
		}
		
		ParseStructType type;		
		if(StringParseStructTypeMap.containsKey(typeStr)){
			List<ParseStructType> matchedTypeCol = null;
			//treat hypo specially to differentiate bewteen HYP and HYP_iff
			if(!typeStr.equals("hypo")){
				matchedTypeCol = StringParseStructTypeMap.get(typeStr);
				
			}else{		
				if(struct.prev1() instanceof StructA){
					//in case typeStr == "hypo", need to differentiate between "if" and "iff"
					//the prev1 should be a StructA of type hyp.
					//typeStr = ((StructA<?,?>)struct.prev1()).prev1().toString() ;
					String hypoStr = ((StructA<?,?>)struct.prev1()).getLeftMostChild();
					typeStr = hypoStr.equals("if and only if") ? hypoStr : typeStr;
					matchedTypeCol = StringParseStructTypeMap.get(typeStr);
				}
			}
			//StringParseStructTypeMap is a Multimap
			if(!matchedTypeCol.isEmpty()){
				type = matchedTypeCol.get(0);
			}else{
				type = STM;
			}
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
