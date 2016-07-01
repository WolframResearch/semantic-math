package thmp;

import java.util.ArrayList;

/**
 * Parses to WL Tree. Using more WL-like structures.
 * Uses ParseStrut as nodes.
 * @author yihed
 *
 */
public class ParseToWLTree {

	/**
	 * Searches through parse tree and matches with ParseStruct's.
	 * Convert to visitor pattern!
	 * 
	 * @param struct
	 * @param parsedSB
	 */
	public static void dfs(Struct struct, StringBuilder parsedSB) {
		// use visitor pattern
		
		if (struct instanceof StructA) {
			//create ParseStruct's
			//the type T will depend on children. The type depends on struct's type
			//figure out types now, fill in later. 
			ParseStructType type = null;
			
			ParseStruct parseStruct = new ParseStruct(struct   , );
			
			System.out.print(struct.type());
			parsedSB.append(struct.type());
			
			System.out.print("[");
			parsedSB.append("[");
			
			// don't know type at compile time
			if (struct.prev1() instanceof Struct) {
				dfs((Struct) struct.prev1(), parsedSB);
			}

			// if(struct.prev2() != null && !struct.prev2().equals(""))
			// System.out.print(", ");
			if (((StructA<?, ?>) struct).prev2() instanceof Struct) {
				// avoid printing is[is], ie case when parent has same type as
				// child
				System.out.print(", ");
				parsedSB.append(", ");
				dfs((Struct) struct.prev2(), parsedSB);
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

				dfs(children.get(i), parsedSB);
			}
			System.out.print("]");
			parsedSB.append("]");
		}
	}
	
}
