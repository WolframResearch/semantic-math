package thmp;
/**
 * Enum for type of ParseStruct. can be 
 * STRUCT -- MathematicalStructure.
 * THM, HYP, DEF, PROP.
 * Can also be "Restriction" 
 * 
 * @author yihed
 *
 */


public enum ParseStructType {
	//thm and prop are prety much the same (in terms of structure)
	THM, PROP, DEF, HYP, PPT, RES;
	
}
