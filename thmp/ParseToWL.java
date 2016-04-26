package thmp;

import java.util.ArrayList;

/**
 * Transforms parse tree to WL
 * @author yihed
 *
 */

public class ParseToWL {
	
	/**
	 * 
	 * @param headStruct is head of a parse
	 */
	public static void parseToWL(Struct headStruct){
		
		if(headStruct == null) return;
		
		//if structH
		if( headStruct.struct().size() > 0 ){
			//System.out.print(" [");

			System.out.print(headStruct.present());		
			
			ArrayList<Struct> children = headStruct.children();
			ArrayList<String> childRelation = headStruct.childRelation();				
			
			if( children != null && children.size() > 0 ){
				for(int i = 0; i < children.size(); i++){
					switch(childRelation.get(i)){
					case "of":
						System.out.print(", ");
						parseToWL(children.get(i));
						break;
					
					default:
						System.out.print(childRelation.get(i) + " ");
						parseToWL(children.get(i));
						//System.out.print(" ]");
					}
				}
			}
			System.out.print("]");

		}
		else { //if structA
			
			//System.out.print(headStruct.type());
			boolean showParen = false, showPrev1 = true;
			
			String type = headStruct.type();
			switch(headStruct.type()){
			case "assert":
				if(headStruct.prev1() != null &&
					((Struct)headStruct.prev2()).prev1().equals("is") ){
					
				}
				break;
			case "verbphrase":
				type = "";
				break;
			case "verb":				
				type = "";
				break;
			case "if":
				showPrev1 = false;
				break;
			case "If":
				showPrev1 = false;
				break;
			case "thenstate":
				showPrev1 = false;
				type = "then";
				break;
			case "pro": type = ""; break;
			case "then":
				showPrev1 = false;
				break;
			case "adj": type = ""; break;
			default:
				showParen = true;
					
			}
			
			System.out.print(type);
			if(showParen || !showPrev1) System.out.print("[");
			
			if(headStruct.prev1() != null && showPrev1){
				if(headStruct.prev1() instanceof Struct){
					parseToWL((Struct)headStruct.prev1());

				}
				else if(headStruct.prev1() instanceof String && !headStruct.prev1().equals("")){
				
					System.out.print(headStruct.prev1());
				}
				if(headStruct.prev2() != null && headStruct.prev2().equals("") && showParen){
					System.out.print("]");
				}
			}
			
			if(headStruct.prev2() != null){
				
				if(headStruct.prev2() instanceof Struct){
					if(showParen)
						System.out.print(", ");
					else
						System.out.print(" ");
					parseToWL((Struct)headStruct.prev2());
					if(showParen) System.out.print("]");
				}
				else if(headStruct.prev2() instanceof String && !headStruct.prev2().equals("")){
					if(showParen)
						System.out.print(", ");
					else
						System.out.print(" ");
					System.out.print(headStruct.prev2());
					if(showParen) System.out.print("]");
				}
			}
			
			//if(headStruct.prev2() != null && headStruct.prev2().equals("") && showParen){
			//	System.out.print("]");
			//}
			if(!showPrev1) System.out.print("]");
		}
	}	
	
}
