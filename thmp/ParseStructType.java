package thmp;

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
	THM, DEF, HYP, PPT, RES, STM, NONE;

	public static ParseStructType getType(String s) {
		ParseStructType type;
		switch (s) {
		case "letbe":
			type = HYP;
			break;
		case "hyp":
			type = HYP;
			break;
		default:
			type = NONE;
		}
		return type;
	}
}
