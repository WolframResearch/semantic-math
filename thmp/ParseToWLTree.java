package thmp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.WLCommand.PosTerm;
import thmp.WLCommand.WLCommandComponent;

/**
 * Parses to WL Tree. Using more WL-like structures.
 * Uses ParseStrut as nodes.
 * 
 * @author yihed
 *
 */
public class ParseToWLTree {
	
	/**
	 * ArrayList used as Stack to store the Struct's that's being processed. 
	 * Pop off after all required terms in a WL command are met.
	 * Each level keeps a reference to some index of the deque.
	 * 
	 */
	private static List<Struct> structDeque;	
	
	/**
	 * List to keep track all triggered WLCommands
	 */
	private static List<WLCommand> WLCommandList;
	
	/**
	 * Trigger words transmission map.
	 */
	private static final Multimap<String, String> triggerWordLookupMap = WLCommandsList.triggerWordLookupMap();
	
	/**
	 * Trigger word lookup map.
	 */
	private static final Multimap<String, WLCommand> WLCommandMap = WLCommandsList.WLCommandMap();
	
	/**
	 * Entry point for depth first search.
	 * @param struct
	 * @param parsedSB
	 * @param headParseStruct
	 * @param numSpaces
	 */
	public static void dfs(Struct struct, StringBuilder parsedSB, ParseStruct headParseStruct, int numSpaces) {
		structDeque = new ArrayList<Struct>();
		WLCommandList = new ArrayList<WLCommand>();
		dfs(struct, parsedSB, headParseStruct, numSpaces, structDeque, WLCommandList);
	}
		
	/**
	 * Get the triggered collection 
	 * @param struct
	 */
	private static Collection<WLCommand> get_triggerCol(String triggerKeyWord){
		
		Collection<WLCommand> triggeredCol;

		triggeredCol = WLCommandMap.get(triggerKeyWord);
		
		if(triggeredCol.isEmpty()){
			if(triggerWordLookupMap.containsKey(triggerKeyWord)){
				
				triggeredCol = new ArrayList<WLCommand>();
			//look up again with fetched list of keywords
			
			Collection<String> col= triggerWordLookupMap.get(triggerKeyWord);
			for(String s : col){
				triggeredCol.addAll(WLCommandMap.get(s));
			}
			}
		}
		return triggeredCol;
	}
	
	/**
	 * Searches through parse tree and matches with ParseStruct's.
	 * Convert to visitor pattern!
	 * 
	 * @param struct
	 * @param parsedSB String
	 * @param headStruct the nearest ParseStruct that's collecting parses
	 * @param numSpaces is the number of spaces to print. Increment space if number is 
	 */
	public static void dfs(Struct struct, StringBuilder parsedSB, ParseStruct headParseStruct, int numSpaces,
			List<Struct> structDeque, List<WLCommand> WLCommandList) {
		//index used to keep track of where in Deque this stuct is
		//to pop off at correct index later
		int structDequeIndex = structDeque.size();
		
		//list of commands satisfied at this level
		List<WLCommand> satisfiedCommands = new ArrayList<WLCommand>();
		
		//add struct to all WLCommands in WLCommandList
		//check if satisfied. 
		//Skip if immediate parents are conj or disj, ie already been added
		if (struct.parentStruct() == null 
				|| (struct.parentStruct() != null && !struct.parentStruct().type().matches("conj.*|disj.*"))) {

			Iterator<WLCommand> WLCommandListIter = WLCommandList.iterator();
			while (WLCommandListIter.hasNext()) {
				WLCommand curCommand = WLCommandListIter.next();
				boolean commandSat = WLCommand.addComponent(curCommand, struct);

				// if commandSat, remove all the waiting, triggered commands for
				// now, except current struct,
				// Use the one triggered first, which comes first in
				// WLCommandList.
				// But what if a longer WLCommand later fits better? Like
				// "derivative of f wrt x"
				if (commandSat) {
					satisfiedCommands.add(curCommand);
					WLCommandListIter.remove();
				}
			}
		}
		
		
		String triggerKeyWord = "";
		if (struct instanceof StructA && struct.prev1() instanceof String) {
			triggerKeyWord = (String)struct.prev1();			
		}else if(struct instanceof StructH){
			triggerKeyWord = struct.struct().get("name");
		}
		
		//if trigger a WLCommand, 
		boolean isTrigger = false;
		Collection<WLCommand> triggeredCol = get_triggerCol(triggerKeyWord);

		if(triggeredCol.isEmpty() && triggerKeyWord.length() > 1 
				&& triggerKeyWord.charAt(triggerKeyWord.length() - 1) == 's'){
				//need to write out all other cases, like ending in "es"
				String triggerWordSingular = triggerKeyWord.substring(0, triggerKeyWord.length() - 1);
				triggeredCol = get_triggerCol(triggerWordSingular);
				
		}
		
		if(!triggeredCol.isEmpty()){			
			//is trigger, add all commands in list 
			//WLCommand curCommand;
			for(WLCommand curCommandInCol : triggeredCol){
				//Copy the WLCommands! So not to modify the ones in WLCommandMap
				WLCommand curCommand = WLCommand.copy(curCommandInCol);
				
				//backtrack until either stop words (ones that trigger ParseStructs) are reached.
				//or the beginning of structDeque is reached.
				//or until commands prior to triggerWordIndex are filled.
				List<PosTerm> posTermList = WLCommand.posTermList(curCommand);
				int triggerWordIndex = WLCommand.triggerWordIndex(curCommand);
				//whether terms prior to trigger word are satisfied
				boolean curCommandSat = true;
				//list of structs waiting to be inserted to curCommand via addComponent
				//temporary list instead of adding directly, since the terms prior need 
				//to be added backwards (always add at beginning), and list will not be 
				//added if !curCommandSat.
				List<Struct> waitingStructList = new ArrayList<Struct>();
				//array of booleans to keep track of which deque Struct's have been used
				boolean[] usedStructsBool = new boolean[structDeque.size()];

				//start from the word before the trigger word
				//iterate through posTermList
				//start index for next iteration of posTermListLoop
				int structDequeStartIndex = structDeque.size() - 1;
				posTermListLoop: for(int i = triggerWordIndex - 1; i > -1; i--){
					PosTerm curPosTerm = posTermList.get(i);
					//auxilliary term
					if(curPosTerm.positionInMap() == -1) continue;
					
					WLCommandComponent curCommandComponent = curPosTerm.commandComponent();
					
					//int curStructDequeIndex = structDequeIndex;
					//iterate through Deque backwards
					//Iterator<Struct> dequeReverseIter = structDeque.descendingIterator();
					int dequeIterCounter = structDeque.size() - 1;
					
					for(int k = structDequeStartIndex; k > -1; k--){
					//for each struct in deque, go through list to match
					//Need a way to tell if all filled
						Struct curStructInDeque = structDeque.get(k);
						//avoid repeating this: 
						String nameStr = "";
						if(curStructInDeque instanceof StructA && curStructInDeque.prev1() instanceof String){
							nameStr = (String)curStructInDeque.prev1();
						}else if(curStructInDeque instanceof StructH){
							nameStr = curStructInDeque.struct().get("name");
						}
						
						String curStructInDequeType = curStructInDeque.type().matches("conj_.*|disj_.*") ?
								curStructInDeque.type().split("_")[1] : curStructInDeque.type();
						
						if(curStructInDequeType.matches(curCommandComponent.posTerm())
								&& nameStr.matches(curCommandComponent.name()) 
								&& !usedStructsBool[dequeIterCounter] ){
							//&& curStructInDeque.name().matches(curCommandComponent.name())
							//see if name matches, if match, move on, continue outer loop
							//need a way to mark structs already matched! 
							
							//add struct to the matching Component if found a match							
							//add at beginning since iterating backwards
							waitingStructList.add(0, curStructInDeque);
							curPosTerm.set_posTermStruct(curStructInDeque);
							
							usedStructsBool[dequeIterCounter] = true;
							structDequeStartIndex = k - 1;
							continue posTermListLoop;
						}
						dequeIterCounter--;
					}
					curCommandSat = false;
					//done iterating through deque, but no match found; curCommand cannot be satisfied
					break;
				}
				//curCommand's terms before trigger word are satisfied. 
				if(curCommandSat){
					boolean curCommandSatWhole = false;
					for(Struct curStruct : waitingStructList){
						//the whole command is satisfied, not the the part before trigger word
						//namely the trigger word is last word
						curCommandSatWhole = WLCommand.addComponent(curCommand, curStruct);						
					}
					if(curCommandSatWhole){
						satisfiedCommands.add(curCommand);
					}else{
						WLCommandList.add(curCommand);
					}
				}
			}			
			isTrigger = true;			
		}
		
			//add struct to stack, even if trigger Struct
		//if(struct.parentStruct() == null || (struct.parentStruct() != null && !struct.parentStruct().type().matches("conj.*|disj.*"))){
			structDeque.add(struct);
		//}
		
		// use visitor pattern!		
		if (struct instanceof StructA) {
			//create ParseStruct's
			//the type T will depend on children. The type depends on struct's type
			//figure out types now, fill in later to ParseStruct later. 
			
			//ParseStructType parseStructType = ParseStructType.getType(struct.type());
			//ListMultimap<ParseStructType, ParseStruct> subParseTree = ArrayListMultimap.create();
			//ParseStruct parseStruct;
			ParseStruct curHeadParseStruct = headParseStruct;
			/*boolean checkParseStructType0 = checkParseStructType(parseStructType);
			if(checkParseStructType0){
				curHeadParseStruct = new ParseStruct(parseStructType, "", struct);
				headParseStruct.addToSubtree(parseStructType, curHeadParseStruct);
				//set to "" so to not print duplicates
				//struct.set_prev1("");
				
				numSpaces++;
				String space = "";
				for(int i = 0; i < numSpaces; i++) space += " ";
				System.out.print("\n " + space + struct.type() + ":>");
				parsedSB.append("\n" + space);	
			}		*/
			
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
				//ParseStruct curHeadParseStruct = headParseStruct;
				//check if need to create new ParseStruct
				String prev1Type = ((Struct)struct.prev1()).type();
				ParseStructType parseStructType = ParseStructType.getType(prev1Type);
				boolean checkParseStructType = checkParseStructType(parseStructType);
				if(checkParseStructType){
					curHeadParseStruct = new ParseStruct(parseStructType, "", (Struct)struct.prev1());
					headParseStruct.addToSubtree(parseStructType, curHeadParseStruct);
					//set to "" so to not print duplicates
					//struct.set_prev1("");
					
					numSpaces++;
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					//System.out.println(space);
					System.out.print("\n " + space + prev1Type + ":>");
					parsedSB.append("\n " + space + prev1Type + ":>");	
				}				
				//set parent for this DFS path. The parent can change on each path!
				((Struct) struct.prev1()).set_parentStruct(struct);				
				//pass along headStruct, unless created new one here				
				dfs((Struct) struct.prev1(), parsedSB, curHeadParseStruct, numSpaces, structDeque, WLCommandList);
				if(checkParseStructType){
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					System.out.println(space);
				}
			}
			
			// if(struct.prev2() != null && !struct.prev2().equals(""))
			// System.out.print(", ");
			if (struct.prev2() instanceof Struct) {
				
				System.out.print(", ");
				parsedSB.append(", ");
				
				// avoid printing is[is], ie case when parent has same type as
				// child
				String prev2Type = ((Struct)struct.prev2()).type();
				ParseStructType parseStructType = ParseStructType.getType(prev2Type);
				curHeadParseStruct = headParseStruct;
				//check if need to create new ParseStruct
				boolean checkParseStructType = checkParseStructType(parseStructType);
				if(checkParseStructType){
					curHeadParseStruct = new ParseStruct(parseStructType, "", (Struct)struct.prev2());
					headParseStruct.addToSubtree(parseStructType, curHeadParseStruct);
					
					numSpaces++;
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					System.out.print("\n " + space + prev2Type + ":>");
					parsedSB.append("\n" + space + prev2Type + ":>");	
				}
				
				((Struct) struct.prev2()).set_parentStruct(struct);				
				dfs((Struct) struct.prev2(), parsedSB, curHeadParseStruct, numSpaces, structDeque, WLCommandList);
				if(checkParseStructType){
					//setting to "" is necessary to not append duplicate messages, 
					//duplicate meaning appears in both a struct and a parsedStruct.
					//should set a flag on Struct, rather than modifying original basic 
					//struct structures.
					//struct.set_prev2("");
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					System.out.println(space);
				}
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
			
			/*if(checkParseStructType0){
				String space = "";
				for(int i = 0; i < numSpaces; i++) space += " ";
				System.out.println(space);
			}*/
			//create new parseStruct to put in tree
			//if Struct (leaf) and not ParseStruct (overall head), done with subtree and return
			
			
		} else if (struct instanceof StructH) {

			System.out.print(struct.toString());
			parsedSB.append(struct.toString());

			List<Struct> children = struct.children();
			List<String> childRelation = struct.childRelation();

			//if (children == null || children.size() == 0)
				//return;
			if (children != null && children.size() != 0){
				
			System.out.print("[");
			parsedSB.append("[");

			for (int i = 0; i < children.size(); i++) {
				System.out.print(childRelation.get(i) + " ");
				parsedSB.append(childRelation.get(i) + " ");
				Struct ithChild = children.get(i);
				
				Struct childRelationStruct = new StructA<String, String>(childRelation.get(i), "", "pre");
				childRelationStruct.set_parentStruct(struct);
				
				//add child relation as Struct
				structDeque.add(childRelationStruct);
				//add struct to all WLCommands in WLCommandList
				//check if satisfied
				Iterator<WLCommand> ChildWLCommandListIter = WLCommandList.iterator();
				while(ChildWLCommandListIter.hasNext()){
					WLCommand curCommand = ChildWLCommandListIter.next();
					boolean commandSat = WLCommand.addComponent(curCommand, childRelationStruct);
					////add struct to posTerm to posTermList! ////////////
					
					if(commandSat){
						satisfiedCommands.add(curCommand);
						//need to remove from WLCommandList
						ChildWLCommandListIter.remove();
					}
				}
				
				ithChild.set_parentStruct(struct);
				dfs(ithChild, parsedSB, headParseStruct, numSpaces, structDeque, WLCommandList);
			}
			System.out.print("]");
			parsedSB.append("]");
			}
		}
		
		//build the commands now after dfs into subtree
		for(WLCommand curCommand : satisfiedCommands){
			
			//set WLCommandStr in this Struct
			//need to find first Struct in posTermList
			List<PosTerm> posTermList = WLCommand.posTermList(curCommand);
			//to get the parent Struct of the first non-WL Struct
			
			int i = 0;
			while(posTermList.get(i).commandComponent().name().matches("WL|AUX")) i++;
						
			PosTerm firstPosTerm = posTermList.get(i);
			//Struct posTermStruct = posTermList.get(0).posTermStruct(); 
			List<Struct> posTermStructList = WLCommand.getStructList(curCommand, firstPosTerm.commandComponent());
			//System.out.println(firstPosTerm);
			//currently just get the first Struct in list, not canonical at all			
			Struct posTermStruct = posTermStructList.get(0);
			
			Struct structToAppendCommandStr = posTermStruct;
			
			Struct parentStruct = posTermStruct.parentStruct();
			
			//go one level higher if parent exists
			Struct grandparentStruct = null;
			if(parentStruct != null) grandparentStruct = parentStruct.parentStruct();
			
			structToAppendCommandStr = (grandparentStruct == null ? 
					(parentStruct == null ? structToAppendCommandStr : parentStruct) : 
						(grandparentStruct instanceof StructH ? parentStruct : grandparentStruct));

			String curCommandString = WLCommand.build(curCommand, structToAppendCommandStr);
			
			//now append Str to wrapper inside build()
			//structToAppendCommandStr.append_WLCommandStr(curCommandString);
			
			//parentStruct.append_WLCommandStr(curCommandString);
			//System.out.println(curCommandString);
		}
		
	}
	/**
	 * iterate through the WrapperList backwards, append the first encounter 
		whose structsWithOtherHeadCount() lies above the set threshold.
		don't append if none exists
	 */
	private static void appendWLCommandStr(Struct struct, StringBuilder parsedSB){
		List<WLCommandWrapper> structWrapperList = struct.WLCommandWrapperList();
		int structWrapperListSz = structWrapperList.size();
		for(int i = structWrapperListSz - 1; i > -1; i--){
			WLCommandWrapper curWrapper = structWrapperList.get(i);
			WLCommand curCommand = curWrapper.WLCommand;
			if(WLCommand.structsWithOtherHeadCount(curCommand) 
					> WLCommand.totalComponentCount(curCommand) - 1){
				//System.out.println(struct.WLCommandStr());
				//parsedSB.append(struct.WLCommandStr());
				parsedSB.append(curWrapper.WLCommandStr);
				break;
			}
		}
	}
	/**
	 * DFS for collecting the WLCommandStr's, instead of using the default 
	 * representations of the Struct's. To achieve a presentation that's closer
	 * to WL commands.
	 * @param struct
	 * @param parsedSB
	 */
	public static void dfs(Struct struct, StringBuilder parsedSB, boolean shouldPrint) {
		//don't append if already incorporated into a higher command
		//System.out.print(struct.WLCommandStrVisitedCount());
		//WLComamnd() should not be null if WLCommandStr is not null
		//if(struct.WLCommandStr() != null && struct.WLCommandStrVisitedCount() < 1){		
		if(struct.WLCommandWrapperList() != null && struct.WLCommandStrVisitedCount() < 1){		
			/*if(WLCommand.structsWithOtherHeadCount(struct.WLCommand()) 
				> WLCommand.totalComponentCount(struct.WLCommand()) -2){
				//if(struct.WLCommandStr() != null ){
				parsedSB.append(struct.WLCommandStr());
			} */
			appendWLCommandStr(struct, parsedSB);
			
			shouldPrint = false;
			//reset WLCommandStr back to null, so next 
			//dfs path can create it from scratch
			//no need to do so as wrapper instances are created anew each dfs run
			//struct.clear_WLCommandStr();
			//nested commands should have some Struct in its posList 
			//that already contains sub nested commands' WLCommandStr.
			//return;
		} 
		
		if (struct instanceof StructA) {
			
			if(shouldPrint) parsedSB.append(struct.type());			
			
			if(shouldPrint) parsedSB.append("[");
			
			// don't know type at compile time
			if (struct.prev1() instanceof Struct) {
				dfs((Struct) struct.prev1(), parsedSB, shouldPrint);
			}

			// if(struct.prev2() != null && !struct.prev2().equals(""))
			// System.out.print(", ");
			if (((StructA<?, ?>) struct).prev2() instanceof Struct) {
				// avoid printing is[is], ie case when parent has same type as
				// child
				if(shouldPrint) parsedSB.append(", ");
				dfs((Struct) struct.prev2(), parsedSB, shouldPrint);
			}

			if (struct.prev1() instanceof String) {
				if(shouldPrint) parsedSB.append(struct.prev1());
			}
			if (struct.prev2() instanceof String) {
				if (!struct.prev2().equals("")){
					if(shouldPrint) parsedSB.append(", ");
				}
				if(shouldPrint) parsedSB.append(struct.prev2());
			}

			if(shouldPrint) parsedSB.append("]");
		} else if (struct instanceof StructH) {

			if(shouldPrint) parsedSB.append(struct.toString());

			List<Struct> children = struct.children();
			List<String> childRelation = struct.childRelation();

			if (children == null || children.size() == 0)
				return;

			if(shouldPrint) parsedSB.append("[");

			for (int i = 0; i < children.size(); i++) {
				if(shouldPrint) parsedSB.append(childRelation.get(i) + " ");

				dfs(children.get(i), parsedSB, shouldPrint);
			}
			if(shouldPrint) parsedSB.append("]");
		}
	}
	
	/**
	 * Cleans up after a dfs run, sets relevant properties attached to Struct nodes
	 * to null.
	 */
	public static void dfsCleanUp(Struct struct) {
		
		struct.clear_WLCommandWrapperList();
		struct.clear_WLCommandStrVisitedCount();		
		struct.set_previousBuiltStruct(null);
		struct.set_structToAppendCommandStr(null);
		
		if (struct instanceof StructA) {
			
			if (struct.prev1() instanceof Struct) {
				dfsCleanUp((Struct) struct.prev1());
			}

			if (struct.prev2() instanceof Struct) {				
				dfsCleanUp((Struct) struct.prev2());
			}
			
		} else if (struct instanceof StructH) {

			List<Struct> children = struct.children();
			
			if (children == null || children.size() == 0)
				return;
			
			for (int i = 0; i < children.size(); i++) {
				
				dfsCleanUp(children.get(i));
			}
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
	
	public static class WLCommandWrapper{
		/**
		 * Wraps around a WLCommand to put in list in each Struct,
		 * contains a WLCommand instance, and its index in list,
		 * in the order the commands are built: inner -> outer, earlier ->
		 * later.
		 */
		private WLCommand WLCommand;
		private int listIndex;
		//built command String associated with this command.
		private String WLCommandStr;
		
		public WLCommandWrapper(WLCommand curCommand, int listIndex){			
			this.WLCommand = curCommand;
			this.listIndex = listIndex;
		}
		
		//shouldn't need this
		public void set_listIndex(int index){
			this.listIndex = index;
		}
		
		public int listIndex(){
			return this.listIndex;
		}
		
		public WLCommand WLCommand(){
			return this.WLCommand;
		}
		
		public String WLCommandStr(){
			return this.WLCommandStr;
		}
		
		public void append_WLCommandStr(String WLCommandStr){
			this.WLCommandStr = this.WLCommandStr == null ? "" : this.WLCommandStr;
			this.WLCommandStr += " " + WLCommandStr;
		}		
		
		public void clear_WLCommandStr(){
			this.WLCommandStr = null;
		}
		
	}
}
