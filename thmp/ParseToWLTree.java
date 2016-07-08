package thmp;

import java.util.ArrayList;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Parses to WL Tree. Using more WL-like structures.
 * Uses ParseStrut as nodes.
 * 
 * @author yihed
 *
 */
public class ParseToWLTree {

	/**
	 * Searches through parse tree and matches with ParseStruct's.
	 * Convert to visitor pattern!
	 * 
	 * @param struct
	 * @param parsedSB String
	 * @param headStruct the nearest ParseStruct that's collecting parses
	 * @param numSpaces is the number of spaces to print. Increment space if number is 
	 */
	public static void dfs(Struct struct, StringBuilder parsedSB, ParseStruct headParseStruct, int numSpaces) {
		// use visitor pattern!		
		if (struct instanceof StructA) {
			//create ParseStruct's
			//the type T will depend on children. The type depends on struct's type
			//figure out types now, fill in later to ParseStruct later. 
			
			ParseStructType parseStructType = ParseStructType.getType(struct.type());
			//ListMultimap<ParseStructType, ParseStruct> subParseTree = ArrayListMultimap.create();
			//ParseStruct parseStruct;
			/*
			if(struct.type().matches("hyp|let") ){
				//create new ParseStruct
				//ParseStructType parseStructType = ParseStructType.getType(struct.type());
				ParseStruct newParseStruct = new ParseStruct(parseStructType, "", struct);
				headParseStruct.addToSubtree(parseStructType, newParseStruct);
				
				numSpaces++;
				String space = "";
				for(int i = 0; i < numSpaces; i++) space += " ";
				System.out.println(space);
				parsedSB.append("\n" + space);				
			} */
			
			System.out.print(struct.type());
			parsedSB.append(struct.type());
			
			System.out.print("[");
			parsedSB.append("[");
			
			// don't know type at compile time
			if (struct.prev1() instanceof Struct) {
				ParseStruct curHeadParseStruct = headParseStruct;
				//check if need to create new ParseStruct
				if(checkParseStructType(parseStructType)){
					curHeadParseStruct = new ParseStruct(parseStructType, "", (Struct)struct.prev1());
					headParseStruct.addToSubtree(parseStructType, curHeadParseStruct);
					//set 
					struct.set_prev1("");
					
					numSpaces++;
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					System.out.println(space);
					parsedSB.append("\n" + space);	
				}				
				//pass along headStruct, unless created new one here
				dfs((Struct) struct.prev1(), parsedSB, curHeadParseStruct, numSpaces);
				String space = "";
				for(int i = 0; i < numSpaces; i++) space += " ";
				System.out.println(space);
			}
			
			// if(struct.prev2() != null && !struct.prev2().equals(""))
			// System.out.print(", ");
			if (struct.prev2() instanceof Struct) {
				// avoid printing is[is], ie case when parent has same type as
				// child
				ParseStruct curHeadParseStruct = headParseStruct;
				//check if need to create new ParseStruct
				if(checkParseStructType(parseStructType)){
					curHeadParseStruct = new ParseStruct(parseStructType, "", (Struct)struct.prev2());
					headParseStruct.addToSubtree(parseStructType, curHeadParseStruct);
					struct.set_prev2("");
					
					numSpaces++;
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					System.out.print("\n " + space + struct.type() + ":>");
					parsedSB.append("\n" + space + struct.type() + ":>");	
				}
				
				System.out.print(", ");
				parsedSB.append(", ");
				
				dfs((Struct) struct.prev2(), parsedSB, curHeadParseStruct, numSpaces);
				String space = "";
				for(int i = 0; i < numSpaces; i++) space += " ";
				System.out.println(space);
			}

			if (struct.prev1() instanceof String) {
				System.out.print(struct.prev1());
				parsedSB.append(struct.prev1());
			}
			if (struct.prev2() instanceof String) {
				if (!struct.prev2().equals("")){
					System.out.print(", ");
					parsedSB.append(", ");
				}
				System.out.print(struct.prev2());
				parsedSB.append(struct.prev2());
			}

			System.out.print("]");
			parsedSB.append("]");
			
			//create new parseStruct to put in tree
			//if Struct (leaf) and not ParseStruct (overall head), done with subtree and return
			
			
		} else if (struct instanceof StructH) {

			System.out.print(struct.toString());
			parsedSB.append(struct.toString());

			ArrayList<Struct> children = struct.children();
			ArrayList<String> childRelation = struct.childRelation();

			if (children == null || children.size() == 0)
				return;

			System.out.print("[");
			parsedSB.append("[");

			for (int i = 0; i < children.size(); i++) {
				System.out.print(childRelation.get(i) + " ");
				parsedSB.append(childRelation.get(i) + " ");

				dfs(children.get(i), parsedSB, headParseStruct, numSpaces);
			}
			System.out.print("]");
			parsedSB.append("]");
		}
	}
	
	/**
	 * 
	 * @param type The enum ParseStructType
	 * @return whether to create new ParseStruct to parseStructHead
	 */
	private static boolean checkParseStructType(ParseStructType type){
		boolean createNew = true;
		if(type == ParseStructType.NONE)
			createNew = false;
		return createNew;
	}
}
