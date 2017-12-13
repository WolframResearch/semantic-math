package thmp.test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.ParseStruct;
import thmp.parse.ParseStructType;
import thmp.parse.Struct;
import thmp.parse.WLCommand;
import thmp.parse.WLCommandsList;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.parse.ParseToWLTree.WLCommandWrapper;
import thmp.parse.WLCommand.PosTerm;

/**
 * Check if two parses are the same, by comparing the
 * WLCommands they trigger, as well as the posList.
 * 
 * @author yihed
 *
 */
public class ParseEqualityCheck {

	private static final Logger logger = LogManager.getLogger(ParseState.class);
	
	/**
	 * Result of parses to be serialized.
	 */
	public static class ParseResult implements Serializable{
		
		private static final long serialVersionUID = -3525356017635047097L;
		
		private String inputString;
		private ParseStruct headParseStruct;
		
		public ParseResult(String inputString_, ParseStruct headParseStruct_){
			this.inputString = inputString_;
			this.headParseStruct = headParseStruct_;
		}
		
		public String inputString(){
			return inputString;
		}
		
		public ParseStruct headParseStruct(){
			return headParseStruct;
		}
		
		@Override
		public String toString(){
			return this.inputString + " " + this.headParseStruct;
		}
	}
	
	/**
	 *  Constructed from a WLCommand.
	 */
	public static class SimplifiedPosTermList{
		
		List<List<String>> simplifiedPosTermList;
		//list of children in each posStruct
		List<Integer> childrenNumList;
		
		public SimplifiedPosTermList(List<PosTerm> posTermList){
			simplifiedPosTermList = new ArrayList<List<String>>();
			childrenNumList = new ArrayList<Integer>();
			//keep only the strings and their ordering information about the posTerm
			for(PosTerm posTerm : posTermList){
				int positionInMap = posTerm.positionInMap();
				if(posTerm.isNegativeTerm() || positionInMap == WLCommandsList.AUXINDEX || positionInMap == WLCommandsList.WL_DIRECTIVE_INDEX){
					continue;
				}
				Struct posTermStruct = posTerm.posTermStruct();
				if(null != posTermStruct){
					//simplifiedPosTermList.add(posTermStruct.nameStr()); //could use posTermStruct.contentStrList()		
					simplifiedPosTermList.add(posTermStruct.contentStrList());// 
					childrenNumList.add(posTermStruct.children().size());
				}				
			}
		}
		
		@Override
		public String toString(){
			return simplifiedPosTermList.toString();
		}
		
		@Override
		public int hashCode(){
			return simplifiedPosTermList.hashCode();
		}
		
		@Override
		public boolean equals(Object other){
			//if(true) return false;
			if(!(other instanceof SimplifiedPosTermList)){
				return false;
			}
			SimplifiedPosTermList otherList = (SimplifiedPosTermList)other;
			List<List<String>> list = otherList.simplifiedPosTermList;
			int listSz = list.size();
			if(listSz != simplifiedPosTermList.size()){
				String msg = "simplifiedPosTermList sizes are not the same!";
				logMessage(msg);
				return false;
			}
			List<Integer> otherChildrenNumList = otherList.childrenNumList;
			int otherChildrenNumListSz = otherChildrenNumList.size();
			if(otherChildrenNumListSz != childrenNumList.size()){
				String msg = "childrenNumList sizes are not the same!";
				logMessage(msg);
				return false;
			}
			
			for(int i = 0; i < otherChildrenNumListSz; i++){
				if(otherChildrenNumList.get(i) != childrenNumList.get(i)){
					return false;
				}
			}
			for(int i = 0; i < listSz; i++){
				List<String> term1 = simplifiedPosTermList.get(i);
				List<String> term2 = list.get(i);
				int sz = term1.size();
				if(sz != term2.size()){
					return false;
				}
				for(int j = 0; j < sz; j++){
					if(!term1.get(j).equals(term2.get(j))){
						return false;
					}
				}
			}
			return true;
		}		
	}
	
	public static boolean checkParse(ParseResult parseResult){
		String inputString = parseResult.inputString();
		System.out.println("ParseEqualityCheck - input " + inputString);
		ParseStruct desiredHeadParseStruct = parseResult.headParseStruct();
		boolean isVerbose = false;
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();		
		ParseState parseState = parseStateBuilder.build();
		ParseRun.parseInput(inputString, parseState, isVerbose);
		ParseStruct headParseStruct = parseState.getHeadParseStruct();
		System.out.println("@@@ " + headParseStruct);
		return compareParseStruct(desiredHeadParseStruct, headParseStruct);
	}
	
	/**
	 * Compares the wrapperMMap in parseStruct. Including 
	 * parseStructType, and WLCommandWrapper and its contents, 
	 * e.g. WLCommand, triggerTerm, posList structs.
	 * @return
	 */
	private static boolean compareParseStruct(ParseStruct desiredPS, ParseStruct ps){
		Multimap<ParseStructType, WLCommandWrapper> desiredWlCommandWrapperMMap = desiredPS.getWLCommandWrapperMMap();
		Multimap<ParseStructType, WLCommandWrapper> wlCommandWrapperMMap2 = ps.getWLCommandWrapperMMap();
		
		if(desiredWlCommandWrapperMMap.size() != wlCommandWrapperMMap2.size()){
			System.out.println(desiredWlCommandWrapperMMap);
			System.out.println(wlCommandWrapperMMap2);
			String msg = "ParseEqualityCheck - compareParseStruct(): sizes of maps are different!";
			logMessage(msg);
			return false;
		}
		
		for(ParseStructType parseStructType : desiredWlCommandWrapperMMap.keys()){
			Collection<WLCommandWrapper> wrapperCol = wlCommandWrapperMMap2.get(parseStructType);
			Multimap<String, SimplifiedPosTermList> triggerPosListMap = null;
			if(null != wrapperCol){
				triggerPosListMap = createSimplifiedPosTermListMap(wrapperCol);				
			}else{
				return false;
			}
			Collection<WLCommandWrapper> desiredWrapperCol = desiredWlCommandWrapperMMap.get(parseStructType);
			Multimap<String, SimplifiedPosTermList> desiredTriggerPosListMap = null;
			desiredTriggerPosListMap = createSimplifiedPosTermListMap(desiredWrapperCol);
			System.out.println("triggerPosListMap "+ triggerPosListMap);///
			if(!triggerPosListMap.equals(desiredTriggerPosListMap)){
				//the Multimap is a set multimap, so order of set per key doesn't matter for equality.
				return false;
			}		
			//System.out.println("ParseEqualityCheck: triggerPosListMap "+triggerPosListMap + "\n desiredTriggerPosListMap " + desiredTriggerPosListMap);
		}
		return true;
	}

	/**
	 * @param wrapperCol
	 */
	private static Multimap<String, SimplifiedPosTermList> createSimplifiedPosTermListMap(Collection<WLCommandWrapper> wrapperCol) {
		
		Multimap<String, SimplifiedPosTermList> triggerPosListMap = HashMultimap.create();
		//construct SimplifiedPosTermList from wlCommand coresponding to each wrapper
		for(WLCommandWrapper wrapper : wrapperCol){
			WLCommand wlCommand = wrapper.WLCommand();
			String triggerWord = wlCommand.getTriggerWord();
			SimplifiedPosTermList simplifiedPosTermList = new SimplifiedPosTermList(WLCommand.posTermList(wlCommand));
			triggerPosListMap.put(triggerWord, simplifiedPosTermList);
		}
		return triggerPosListMap;
	}
	
	private static void logMessage(String msg){
		logger.error(msg);
		System.out.println(msg);
	}
	
}
