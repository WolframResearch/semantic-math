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
	
	
	public static void parseToWL(Struct headStruct){
		parseString = "";
		parseToWL(headStruct, true);
	}
	/**
	 * 
	 * @param headStruct is head of a parse
	 */
	public static void parseToWL(Struct headStruct, boolean showPrev1){
		
		if(headStruct == null) return;		
		boolean showprev1 = showPrev1;
		//if structH
		if( headStruct.struct().size() > 0 ){
			//System.out.print(" [");
			
			System.out.print(headStruct.present());		
			parseString += headStruct.present();
			
			ArrayList<Struct> children = headStruct.children();
			ArrayList<String> childRelation = headStruct.childRelation();				
			
			if( children != null && children.size() > 0 ){
				for(int i = 0; i < children.size(); i++){
					switch(childRelation.get(i)){
					case "of":
						System.out.print(", ");
						parseString += ", ";
						parseToWL(children.get(i), showprev1);
						break;
					
					default:
						System.out.print(childRelation.get(i) + " ");
						parseString += childRelation.get(i) + " ";
						parseToWL(children.get(i), showprev1);
						//System.out.print(" ]");
					}
				}
			}
			System.out.print("]");
			parseString += "]";
		}
		else { //if structA
			
			//System.out.print(headStruct.type());
			boolean showParen = true; //showPrev1 = true;
			
			String type = headStruct.type();
			
			switch(headStruct.type()){
			case "assert":
				if(headStruct.prev1() != null &&
					((Struct)headStruct.prev2()).prev1().equals("is") ){
					//possibly recurse right here for this special case
				}
				inAssert = true;
				showParen = true;
				break;
			case "verbphrase":
				if(inAssert ){
					showprev1 = false;
				}
				type = "";				
				break;
			case "verb":	
				if(inAssert ){
					assertVerb = headStruct.prev1().toString();
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
			case "adj": type = ""; break;
			case "let": type = ""; showprev1 = false; break;
			default:
				showParen = true;
					
			}
			
			System.out.print(type);
			parseString += type;
			if(showParen ) System.out.print("[");
			parseString += "[";
			
			if(headStruct.prev1() != null){
				if(headStruct.prev1() instanceof Struct){
					parseToWL((Struct)headStruct.prev1(), showprev1);
				}
				else if(headStruct.prev1() instanceof String && !headStruct.prev1().equals("")){
					
					System.out.print(headStruct.prev1());
					parseString += headStruct.prev1();
				}
				if(headStruct.prev2() != null && headStruct.prev2().equals("") && showParen){
					System.out.print("]");
					parseString += "]";
				}
			}
			
			if(headStruct.prev2() != null){
				
				if(headStruct.prev2() instanceof Struct){
					//if(showParen)
					System.out.print(", ");
					parseString += ", ";
					if(!showParen){
						System.out.print(" ");
						parseString += " ";
					}
					parseToWL((Struct)headStruct.prev2(), showprev1);
					//if(showParen) System.out.print("]");
					
					if(headStruct.type().equals("assert")){
						System.out.print(", "+ assertVerb + "]");
						parseString += ", "+ assertVerb + "]";
						inAssert = false;
					}
				}
				else if(headStruct.prev2() instanceof String && !headStruct.prev2().equals("")){
						System.out.print(", ");
						parseString += ", ";
					
				
					System.out.print(headStruct.prev2());
					parseString += headStruct.prev2();
					//if(showParen) System.out.print("]");
				}
			}
			
			//if(headStruct.prev2() != null && headStruct.prev2().equals("") && showParen){
			//	System.out.print("]");
			//}
			if(!showprev1) {
				System.out.print("]");
				parseString += "]";
			}
		}
	}
	
	public static void processParse(){
		//System.out.println(parseString);
		parseString = parseString.replaceAll("\\[\\],\\s", "");
		parseString = parseString.replaceAll(",\\s\\]", "]");
		System.out.println("***");
		System.out.println(parseString);
		System.out.println("***");
	}
	
}
