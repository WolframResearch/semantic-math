package thmp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Multimap;

/**
 * Methods to parse again, if the first pass in ThmP1.parse()
 * does not produce spanning parses. Iteratively drops elements that
 * are preventing a spanning parse, such as Structs that are not connect-able
 * to either neighboring token.
 * @author yihed
 *
 */
public class ParseAgain {
	
	private static final Multimap<String, Rule> structMap = Maps.structMap();

	/**
	 * 
	 * @param structList
	 * @param structCoordinates Coordinates in mx of Structs contained in structList.
	 * @param parseState
	 */
	public static void parseAgain(List<Struct> structList, List<int[]> structCoordinates, 
			ParseState parseState, List<List<StructList>> mx, List<Struct> originalNonSpanningParseStructList){
		//pick up Structs that are not connected to either neighbor,
		//which are those whose left neighbor has row column index i-1, 
		//and whose right neighbor has row index i+1.
		List<Struct> loneStructList = new ArrayList<Struct>();
		List<Integer> loneStructIndexList = new ArrayList<Integer>();
		/*not completely lonely, has one neighbor.*/
		List<Struct> oneNeighborStructList = new ArrayList<Struct>();
		List<Integer> oneNeighborStructIndexList = new ArrayList<Integer>();
		
		//List<Struct> originalNonSpanningParseStructList = parseState.getTokenList();
		//System.out.println("originalNonSpanningParseStructList len " + originalNonSpanningParseStructList.size());
		
		//array to indicate which ones to exclude, 1 means exclude. Think of as bit vector.
		//gradually turn entries at indices that are entries in loneStructIndexList to 1.
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
			
			int prevCoordinateColIndex = structCoordinates.get(i-1)[1];
			int nextCoordinateRowIndex = structCoordinates.get(i+1)[0];
			//Isolated Struct: i.e. if prev col index is one less than current row,
			//and next row index is one greater than current col.
			if(prevCoordinateColIndex == curRow-1 //<= 
					&& nextCoordinateRowIndex == curCol+1){ //>=
				/*But this condition should be trivially satisfied !!
				 * but in practice it doesn't always, why??*/
				loneStructList.add(struct_i);
				loneStructIndexList.add(i);
			}else {
				oneNeighborStructList.add(struct_i);
				oneNeighborStructIndexList.add(i);				
			}
		}
		System.out.println("ParseAgain - !loneStructList! " + loneStructList);
		/*System.out.println("!structCoordinates! " );
		for(int[] s : structCoordinates){
			 System.out.println(Arrays.toString(s));
		}
		System.out.println(structList);*/
		//iterate through combinations, in each of which a subset 
		//of loneStructList is missing.
		//all possible combinations amount to 2^m, where
		//m is size of loneStructList.
		//keep list of lists of Structs, skip around, skip subsets of 
		//loneStructIndexList.
		if(!loneStructList.isEmpty()){
			recurseParse(structList, indicesToExclude, loneStructIndexList, parseState);			
		}else if(!oneNeighborStructList.isEmpty()){
			recurseParse(structList, indicesToExclude, oneNeighborStructIndexList, parseState);
		}
		/*Returned from recursion, but still no spanning parse. Then iteratively drop elements, to see
		 * if their *neighbors* form a valid pair, if so, drop the middle Struct.*/
		if(!parseState.isRecentParseSpanning()){
			/*remove the ends of the Struct's. Ignore singletons, as they would have been removed*/
			List<Struct> droppingStructList = new ArrayList<Struct>();
			List<Integer> indexToDropList = new ArrayList<Integer>();
			//List<Integer> indexToKeepList = new ArrayList<Integer>();
			
			coordinatesLoop: for(int p = 0; p < structCoordinates.size()-1; p++){
				int[] ithCoordinates = structCoordinates.get(p);
				int[] nextCoordinates = structCoordinates.get(p+1);
				int i = ithCoordinates[0];
				int j = ithCoordinates[1];
				
				int k = nextCoordinates[0];
				int l = nextCoordinates[1];
				//diagonal tokens would have already been tried in dropping in above reparse
				
				assert k == j + 1;
				//drop lower right corner of ij block.
				if(i < j){
					StructList ijStructList = mx.get(i).get(j-1);
					if(0 == ijStructList.size()){
						//System.out.println("ParseAgain-originalNonSpanningParseStructList: " + originalNonSpanningParseStructList);
						ijStructList = new StructList(originalNonSpanningParseStructList.get(j-1));
					}
					/*Note that klStructList *cannot* be empty, because they are coordinates passed in.*/
					StructList klStructList = mx.get(k).get(l);					
					if(addToDropList(indexToDropList, ijStructList, klStructList, j)){
						continue coordinatesLoop;
					}
				}								
				if(k < l){
					StructList ijStructList = mx.get(i).get(j);
					StructList klStructList = mx.get(k+1).get(l);
					if(0 == klStructList.size()){
						klStructList = new StructList(originalNonSpanningParseStructList.get(k+1));
					}
					if(addToDropList(indexToDropList, ijStructList, klStructList, k)){
						continue coordinatesLoop;
					}
				}
				//Check dropping the corners from both ij block and the kl block
				if(i < j && k < l){
					StructList ijStructList = mx.get(i).get(j-1);
					if(0 == ijStructList.size()){
						ijStructList = new StructList(originalNonSpanningParseStructList.get(j-1));
					}
					StructList klStructList = mx.get(k+1).get(l);
					if(0 == klStructList.size()){
						klStructList = new StructList(originalNonSpanningParseStructList.get(k+1));
					}
					//if(true) throw new IllegalStateException(ijStructList.toString() + " .. " + klStructList);
					if(addToDropList(indexToDropList, ijStructList, klStructList, j, k)){
						continue coordinatesLoop;
					}
				}
			}
			int dropCounter = 0;
			for(int i = 0; i < originalNonSpanningParseStructList.size(); i++){
				/*This assumes indexToDropList is ordered, as it should the way indices were added*/
				if(dropCounter < indexToDropList.size() && i == indexToDropList.get(dropCounter)){
					dropCounter++;
					continue;
				}
				droppingStructList.add(originalNonSpanningParseStructList.get(i));
			}
			
			parseState.setTokenList(droppingStructList);
			boolean isReparse = true;
			ThmP1.parse(parseState, isReparse);			
		}
	}

	/**
	 * Dropping if pos pair doesn't can't be combined in any way. ??
	 * @param indexToDropList
	 * @param indexToDrop 1 or 2 integers
	 * @param ijStructList
	 * @param klStructList
	 */
	public static boolean addToDropList(List<Integer> indexToDropList, StructList ijStructList,
			StructList klStructList, int... indexToDrop) {
		boolean dropped = false;
		outerloop: for(Struct ijStruct : ijStructList.structList()){
			for(Struct klStruct : klStructList.structList()){
				String posCombined = ijStruct.type() + "_" + klStruct.type();
				if(structMap.containsKey(posCombined)){
					for(int index : indexToDrop){
						indexToDropList.add(index);						
					}
					dropped = true;
					break outerloop;
				}
			}
		}
		return dropped;
	}
	
	private static void recurseParse(List<Struct> structList, int[] indicesToExclude,
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
		
		recurseParse(structList, indicesToExclude, loneStructIndexList, parseState);
		
	}
}
