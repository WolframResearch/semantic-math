package thmp;

import java.util.ArrayList;

/**
 * Transforms parse tree to WL
 * @author yihed
 *
 */

public class ParseToWL {
	
	private static boolean inAssert = false;
	private static String assertVerb = "";
	//private static boolean showPrev1 = true;
	public static String parseString = "";	
	
	public static String parseToWL(Struct headStruct){
		parseString = "";
		parseToWL(headStruct, true);
		System.out.println();
		processParse();
		return parseString;
	}
	
	/**
	 * 
	 * @param headStruct is head of a parse
	 */
	public static void parseToWL(Struct headStruct, boolean showPrev1){
		
		//used to tell the caller whether the verb is collected or not in asserts
		//boolean showPrev1Return = showPrev1;
		if(headStruct == null) return;		
		boolean showprev1 = showPrev1;
		
		//if structH
		if( headStruct.struct() != null && headStruct.struct().size() > 0 ){
			
			System.out.print(headStruct.present(""));		
			parseString += headStruct.present("");
		}
		else { //if structA
			
			String type = headStruct.type();
			
			switch(headStruct.type()){
			case "assert":
				if(headStruct.prev1() != null &&
					((Struct)headStruct.prev2()).prev1().equals("is") ){
					//possibly recurse right here for this special case
				}
				inAssert = true;
				break;
			case "verbphrase":
				if(inAssert && headStruct.prev1() instanceof Struct 
						&& ((Struct)headStruct.prev1()).type().equals("verb") ){
					showprev1 = false;
				}
				type = "";				
				break;
			case "verb":	
				if(inAssert ){
					assertVerb = headStruct.prev1().toString();
					//showPrev1Return = true;
				}
				showprev1 = false;
				type = "";
				break;
			case "if":
				showprev1 = false;
				break;
			case "If":
				showprev1 = false;
				break;
			case "thenstate":
				showprev1 = false;
				type = "then";
				break;
			case "pro": type = ""; break;
			case "then":
				showprev1 = false;
				break;
			case "iff": type = ""; break;	
			case "rpro": type = ""; break;	
			case "hypo": type = ""; break;
			case "hyp": 
				type = "";
				if(headStruct.prev1() != null && headStruct.prev1() instanceof String
						&& !((String)headStruct.prev1()).matches("for every|for all|for each") ){
					showprev1 = false;
				}
				break;
			case "adj": type = ""; break;
			case "det": type = ""; break;
			case "ppt": type = ""; showprev1 = false; break;
			case "let": type = ""; showprev1 = false; break;
			case "be": type = ""; showprev1 = false; break;
			case "prep": type = ""; break;
			case "pre": type = ""; break;
			default:
					
			}
			
			if(showPrev1){
				System.out.print(type);
				parseString += type;
			}
			if(showprev1){
				System.out.print("[");
				parseString += "[";
			}
			
			if(headStruct.prev1() != null){
				if(headStruct.prev1() instanceof Struct){
					parseToWL((Struct)headStruct.prev1(), showprev1);
					//the false already propagated downwards
					//if(inAssert) showprev1 = true;
				}
				else if(headStruct.prev1() instanceof String && !headStruct.prev1().equals("") 
						&& showprev1){					
					System.out.print(headStruct.prev1());
					parseString += headStruct.prev1();
				}
				if(headStruct.prev2() != null && headStruct.prev2().equals("") && showPrev1){////
					System.out.print("*]*");
					parseString += "]";
				}
				if(headStruct.prev2() != null && !headStruct.prev2().equals("") && showprev1){
					System.out.print(", ");
					parseString += ", ";
				}
				//if(headStruct.prev1() instanceof Struct && inAssert) showprev1 = true;
			}
			
			if(headStruct.prev2() != null){
				
				if(headStruct.prev2() instanceof Struct){
					
					parseToWL((Struct)headStruct.prev2(), showPrev1);////
					
					if(headStruct.type().equals("assert")){
						System.out.print(", "+ assertVerb );
						parseString += ", "+ assertVerb;
						//showPrev1Return = true;
						inAssert = false;
					}
					
					if(showprev1 ){
						System.out.print("`]");
						parseString += "]";
					}
				}
				else if(headStruct.prev2() instanceof String && !headStruct.prev2().equals("")){						
				
					System.out.print(headStruct.prev2());
					parseString += headStruct.prev2();
					if(showprev1){ 
						System.out.print("~]");
						parseString += "]";					
					}
				}
			}
		}
	}
	
	public static void processParse(){
		//System.out.println(parseString);
		//Expensive operations! Try faster ways, maybe Pattern matchers
		parseString = parseString.replaceAll("\\[\\],\\s|\\[\\]", "");
		
		parseString = parseString.replaceAll(",\\s\\]", "]").
				replaceAll(",\\s,\\s", ", ").replaceAll("\\[([a-zA-Z]*)\\],", "$1,");
		System.out.println(parseString);
	}
	
}
