package thmp.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import thmp.parse.Struct;
import thmp.parse.StructList;

/**
 * Plot utilities to assist with e.g. debugging.
 * Not displaying childRelations or properties/qualifiers (that are not children) 
 * to not obfuscate the graph.
 * @author yihed
 *
 */
public class PlotUtils {

	//the max number of chars allowed to show a particular Struct.
	private static final int STRUCT_WIDTH = 12;
	//max number of chars allowed for a StructList.
	private static final int STRUCTLIST_WIDTH = 17;
	
	private static class PlotStructWrapper{
		private Struct struct; //head struct
		private int descendentStart;
		private int descendentEnd;
		
		public PlotStructWrapper(Struct struct_, int left, int right){
			this.struct = struct_;
			this.descendentStart = left;
			this.descendentEnd = right;
		}
		
		public void setDescendentStart(int descendentStart) {
			this.descendentStart = descendentStart;
		}
		
		@Override
		public String toString(){
			
			String str = struct.nameStr();
			if("".equals(str)){
				str = struct.type();								
			}
			int tempStrLen = str.length();
			int lenDiff = STRUCT_WIDTH - tempStrLen;
			if(lenDiff > 0){
				StringBuilder sb = new StringBuilder();
				for(int i = 0; i < lenDiff; i++){
					sb.append(" ");
				}
				str = sb.insert(0, str).toString();					
			}else{
				str = str.substring(0, STRUCT_WIDTH);
			}
			return str;
			//return struct.toString().substring(0, STRUCT_WIDTH);
			//return struct.nameStr().substring(0, struct.nameStr().length());
		}		
	}
	
	/**
	 * Plot parse matrix mx produced in ThmP1.parse().
	 * The matrix diagonal may not contain all terms
	 * in original input.
	 * e.g. "the polynomial $f$ has one root" gives: 
	 * polynomial                 assert           
                 has              verbphrase       
                                  root    |root 
	 * @param mx
	 */
	public static void plotMx(List<List<StructList>> mx){
		System.out.println("Plot of matrix mx: ");
		int mxSz = mx.size();
		for(int i = 0; i < mxSz; i++){
			List<StructList> list = mx.get(i);
			for(StructList structList : list){
				structListToShortString(structList);
			}
			System.out.println();
		}
	}
	
	private static void structListToShortString(StructList structList){
		List<Struct> list = structList.structList();
		int listSz = list.size();
		StringBuilder sb = new StringBuilder(200);
		if(0 == listSz){			
			for(int i = 0; i < STRUCTLIST_WIDTH; i++){
				sb.append(" ");
			}			
			System.out.print(sb);
			return;
		}
		int widthPerStruct = STRUCTLIST_WIDTH/listSz;
		widthPerStruct = widthPerStruct == 0 ? 1 : widthPerStruct;
		for(int i = 0; i < listSz; i++){
			//sb.append("[");
			Struct struct = list.get(i);
			String str = struct.nameStr();
			
			if("".equals(str)){
				str = struct.type();
			}			
			int strLen = str.length();
			if(strLen < widthPerStruct){
				sb.append(str);
				int tempLen = strLen;
				while(tempLen < widthPerStruct){
					tempLen++;
					sb.append(" ");
				}
			}else{
				str = str.substring(0, widthPerStruct);
				sb.append(str);
			}
			sb.append("|");					
		}
		System.out.print(sb.substring(0, sb.length()-1));	
	}
	
	public static void plotTree(Struct headStruct){
		//S s = new S(headStruct);
		ListMultimap<Integer, PlotStructWrapper> sMMap = ArrayListMultimap.create();
		List<Integer> bottomRowIndexList = new ArrayList<Integer>();
		bottomRowIndexList.add(0);
		constructSTree(headStruct, sMMap, bottomRowIndexList, 0);
		plotTree(sMMap);
	}
	
	static PlotStructWrapper constructSTree(Struct struct, ListMultimap<Integer, PlotStructWrapper> sMMap, List<Integer> bottomRowIndexList, int level){
		
		int left = bottomRowIndexList.get(0);
		int right = bottomRowIndexList.get(0);
		//int index = bottomRowIndexList.get(0);
		
		//if no descendant
		if(!struct.prev1NodeType().isTypeStruct() && !struct.prev2NodeType().isTypeStruct() 
				&& !struct.has_child()){
			int index = bottomRowIndexList.get(0);
			PlotStructWrapper s = new PlotStructWrapper(struct, index, index);
			//sList.add(s);
			bottomRowIndexList.set(0, index+1);
			sMMap.put(level, s);
			return s;
		}
		
		if(struct.prev1NodeType().isTypeStruct()){
			PlotStructWrapper prev1S = constructSTree((Struct)struct.prev1(), sMMap, bottomRowIndexList, level+1);
			//sMMap.put(level, prev1S);
			//bottomRowIndex.set(0, bottomRowIndex.get(0)+1);
			//index = bottomRowIndexList.get(0)+1;
			left = prev1S.descendentStart;			
		}
		
		if(struct.prev2NodeType().isTypeStruct()){			
			PlotStructWrapper prev1S = constructSTree((Struct)struct.prev2(), sMMap, bottomRowIndexList, level+1);
			//sMMap.put(level+1, prev1S);
			//bottomRowIndex.set(0, bottomRowIndex.get(0)+1);
			//index = bottomRowIndexList.get(0)+1;
			right = prev1S.descendentEnd;			
		}
		List<Struct> children = struct.children();
		int childrenLen = struct.children().size();
		for(int i = 0; i < childrenLen; i++){
			Struct child = children.get(i);
			PlotStructWrapper prev1S = constructSTree(child, sMMap, bottomRowIndexList, level+1);
			//sMMap.put(level+1, prev1S);
			//bottomRowIndex.set(0, bottomRowIndex.get(0)+1);
			//index = bottomRowIndexList.get(0)+1;
			right = prev1S.descendentEnd;
		}
		PlotStructWrapper wrapper = new PlotStructWrapper(struct, left, right);
		sMMap.put(level, wrapper);
		return wrapper;
	}
	
	static void plotTree(ListMultimap<Integer, PlotStructWrapper> sMMap){
		//Map<Integer, Collection<S>> sMap = sMMap.asMap();
		int sMMapLen = sMMap.keySet().size();
		for(int i = 0; i < sMMapLen; i++){
			Collection<PlotStructWrapper> col = sMMap.get(i);
			int curPos = 0;
			for(PlotStructWrapper s : col){
				int left = s.descendentStart;
				int right = s.descendentEnd;				
				int desired = (int)((right + left)/(double)2 * (STRUCT_WIDTH));
				//System.out.println("S struct: " + s.struct +  "  left "+left + " right " +right + " desired: "+desired);
				while(curPos < desired){
					System.out.print(" ");
					curPos++;
				}
				System.out.print(s.toString());
				curPos += STRUCT_WIDTH;
			}
			System.out.println();
		}
	}
	
}
