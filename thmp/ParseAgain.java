package thmp;

import java.util.ArrayList;
import java.util.List;

/**
 * Methods to parse again, if the first pass in ThmP1.parse()
 * does not produce spanning parses. Iteratively drops elements that
 * are preventing a spanning parse, such as Structs that are not connect-able
 * to either neighboring token.
 * @author yihed
 *
 */
public class ParseAgain {
	
	/**
	 * 
	 * @param structList
	 * @param structCoordinates Coordinates in mx of Structs contained in structList.
	 * @param parseState
	 */
	public static void parseAgain(List<Struct> structList, List<int[]> structCoordinates, 
			ParseState parseState){
		//pick up Structs that are not connected to either neighbor,
		//which are those whose left neighbor has row column index i-1, 
		//and whose right neighbor has row index i+1.
		List<Struct> loneStructList = new ArrayList<Struct>();
		List<Integer> loneStructIndexList = new ArrayList<Integer>();
		//array to indicate which ones to exclude, 1 means exclude. Think of as bit vector.
		//gradually turn entries at indices that are entries in loneStructIndexLis to 1.
		int[] indicesToExclude = new int[structList.size()];
		
		int structListSz = structList.size();	
		
		//don't need to deal with of first and last elements,
		//since determining whether they are connected to their neighbors
		//is less reliable, and they are dealt with in the later defluffing algorithm.
		
		for(int i = 1; i < structListSz-1; i++){
			
			Struct struct_i = structList.get(i);
			//only if struct_i is either on the diagonal of mx, 
			//or has very small subtree.
			//if either StructA or StructH with children nodes
			if(struct_i.numUnits() > 2 && struct_i.isStructA()){
				continue;				
			}else if(struct_i.descendantHasChild()){
				continue;
			}
			
			int curRow = structCoordinates.get(i)[0];
			int curCol = structCoordinates.get(i)[1];
			//if prev col index is one less than current row,
			//and next row index is one greater than current col.
			if(structCoordinates.get(i-1)[1] == curRow-1 
					&& structCoordinates.get(i+1)[0] == curCol+1){
				loneStructList.add(struct_i);
				loneStructIndexList.add(i);
			}
		}
		
		if(loneStructList.size() > 0){
			indicesToExclude[loneStructIndexList.get(0)] = 1;
			loneStructIndexList = loneStructIndexList.subList(1, loneStructList.size());
		}
		//iterate through combinations, in each of which a subset 
		//of loneStructList is missing.
		//all possible combinations amount to 2^m, where
		//m is size of loneStructList.
		//keep list of lists of Structs, skip around, skip subsets of 
		//loneStructIndexList.
		recurse(structList, indicesToExclude, loneStructIndexList, parseState);
		
	}
	
	private static void recurse(List<Struct> structList, int[] indicesToExclude,
			List<Integer> loneStructIndexList, ParseState parseState){
		
		if(loneStructIndexList.size() == 0){
			return;
		}
		
		indicesToExclude[loneStructIndexList.get(0)] = 1;
		loneStructIndexList = loneStructIndexList.subList(1, loneStructIndexList.size());
		
		//form list of structs
		List<Struct> newStructList = new ArrayList<Struct>();
		for(int i = 0; i < structList.size(); i++){
			if(0 == indicesToExclude[i]){
				newStructList.add(structList.get(i));
			}
		}
		
		//if spanning parse with newStructList found, break out of recursion.
		parseState.setTokenList(newStructList);
		boolean isReparse = true;
		ThmP1.parse(parseState, isReparse);
		
		if(parseState.isRecentParseSpanning()){
			return;
		}
		
		recurse(structList, indicesToExclude, loneStructIndexList, parseState);
		
	}
}
