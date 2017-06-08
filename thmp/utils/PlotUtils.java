package thmp.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import thmp.parse.Struct;

/**
 * Plot utilities to assist with e.g. debugging.
 * @author yihed
 *
 */
public class PlotUtils {

	//the max number of chars allowed to show a particular Struct.
	private static final int STRUCT_WIDTH = 12;
	private List<S> sList = new ArrayList<S>();
	
	private static class S{
		private Struct struct; //head struct
		private int descendentStart;
		private int descendentEnd;
		
		public S(Struct struct_, int left, int right){
			this.struct = struct_;
			this.descendentStart = left;
			this.descendentEnd = right;
		}
		
		public void setDescendentStart(int descendentStart) {
			this.descendentStart = descendentStart;
		}
		@Override
		public String toString(){
			return struct.toString().substring(0, STRUCT_WIDTH  );
			//return struct.nameStr().substring(0, struct.nameStr().length());
		}
		
	}
	
	public static void plotTree(Struct headStruct){
		//S s = new S(headStruct);
		ListMultimap<Integer, S> sMMap = ArrayListMultimap.create();
		List<Integer> bottomRowIndexList = new ArrayList<Integer>();
		bottomRowIndexList.add(0);
		S headS = constructSTree(headStruct, sMMap, bottomRowIndexList, 0);
		plotTree(sMMap);
	}
	
	static S constructSTree(Struct struct, ListMultimap<Integer, S> sMMap, List<Integer> bottomRowIndex, int level){
		
		int left = bottomRowIndex.get(0);
		int right = bottomRowIndex.get(0);
		int index = bottomRowIndex.get(0);
		
		//if no descendant
		if(!struct.prev1NodeType().isTypeStruct() && !struct.prev2NodeType().isTypeStruct() 
				&& !struct.has_child()){
			S s = new S(struct, bottomRowIndex.get(0), bottomRowIndex.get(0));
			//sList.add(s);
			bottomRowIndex.set(0, index+1);
			sMMap.put(level, s);
			return s;
		}
		
		if(struct.prev1NodeType().isTypeStruct()){
			S prev1S = constructSTree((Struct)struct.prev1(), sMMap, bottomRowIndex, level+1);
			//sMMap.put(level, prev1S);
			//bottomRowIndex.set(0, bottomRowIndex.get(0)+1);
			index = bottomRowIndex.get(0)+1;
			left = prev1S.descendentStart;			
		}
		
		if(struct.prev2NodeType().isTypeStruct()){
			
			S prev1S = constructSTree((Struct)struct.prev2(), sMMap, bottomRowIndex, level+1);
			//sMMap.put(level+1, prev1S);
			//bottomRowIndex.set(0, bottomRowIndex.get(0)+1);
			index = bottomRowIndex.get(0)+1;
			right = prev1S.descendentEnd;			
		}
		List<Struct> children = struct.children();
		int childrenLen = struct.children().size();
		for(int i = 0; i < childrenLen; i++){
			Struct child = children.get(i);
			boolean isRightMostStruct = false;
			if(i == childrenLen-1){
				isRightMostStruct = true;
			}
			S prev1S = constructSTree(child, sMMap, bottomRowIndex, level+1);
			//sMMap.put(level+1, prev1S);
			//bottomRowIndex.set(0, bottomRowIndex.get(0)+1);
			index = bottomRowIndex.get(0)+1;
			right = prev1S.descendentEnd;
		}
		S s = new S(struct, left, right);
		sMMap.put(level, s);
		return s;
	}
	
	static void plotTree(ListMultimap<Integer, S> sMMap){
		//Map<Integer, Collection<S>> sMap = sMMap.asMap();
		int sMMapLen = sMMap.keySet().size();
		for(int i = 0; i < sMMapLen; i++){
			Collection<S> col = sMMap.get(i);
			int curPos = 0;
			for(S s : col){
				int left = s.descendentStart;
				int right = s.descendentEnd;
				int desired = (int)((right + left)/(double)2 * (STRUCT_WIDTH+1));
				while(curPos < desired){
					System.out.print(" ");
					curPos++;
				}
				System.out.print(s.toString());
			}
			System.out.println();
		}
		
		
	}
	
	/**
	 * Figure out the width (in terms of spaces) of the bottom row.
	 */
	private static int numStructBottom(Struct struct){
		/*if(){
			
		}
		if(struct.prev1NodeType() == NodeType.Struct){
			
		}*/
		return 0;
	}
	
}
