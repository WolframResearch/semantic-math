package thmp;

import java.util.List;

/**
 * parse if current sentence does not produce a spanning
 * parse, or does not satisfy any WLCommand, and that the prior 
 * sentence triggered a particular command, identified by the 
 * trigger word, e.g. "define", "suppose".
 * E.g. "Let $F$ be a field, and $R$ a ring."
 * @author yihed
 *
 */
public class ConditionalParse {

	
	/**
	 * 
	 * @param prevStructList
	 * @param curStructList
	 * @return prevStruct with certain Structs substituted with that Structs
	 * in curStructList.
	 */
	public static List<Struct> superimposeStructList(List<Struct> prevStructList,
			List<Struct> curStructList){
		
		//remove "and" if in e.g. "and $R$ a ring".
		String firstStructType = curStructList.get(0).type();
		if(firstStructType.equals("and") || firstStructType.equals("or")){
			curStructList.remove(0);
		}
		//look through to find similar structs in both lists first?
		
		//walk through list, and substitute as many Struct's in prevStructList 
		//with Structs in curStructList as possible.
		for(int i = 0; i < curStructList.size(); i++){
			//replace only StructH for now
			Struct curStruct = curStructList.get(i);
			
			if(curStruct.isStructA() && !curStruct.type().equals("symb")){
				i++;
				continue;
			}
			
			for(int j = 0; j < prevStructList.size(); j++){
				Struct prevStruct = prevStructList.get(j);
				if(prevStruct.isStructA() && !curStruct.type().equals("symb")){
					j++;
					continue;					
				}else{
					prevStructList.set(j, curStruct);
				}
				
			}			
		}
		return prevStructList;
	}
	
}
