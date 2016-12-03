package thmp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;

import com.google.common.collect.Multimap;

import exceptions.IllegalWLCommandStateException;
import thmp.Struct.ChildRelation;
import thmp.Struct.NodeType;
import thmp.ThmP1.ParsedPair;
import thmp.WLCommand.CommandSat;
import thmp.WLCommand.ImmutableWLCommand;
import thmp.WLCommand.PosTerm;
import thmp.WLCommand.WLCommandComponent;
import thmp.utils.Buggy;

/**
 * Parses to WL Tree. Using more WL-like structures.
 * Uses ParseStrut as nodes.
 * 
 * @author yihed
 *
 */
public class ParseToWLTree{
	
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
	private static final Multimap<String, ImmutableWLCommand> WLCommandMap = WLCommandsList.WLCommandMap();
	
	private static final Logger logger = Buggy.getLogger();
	
	private static final Pattern PLURAL_PATTERN = Pattern.compile("(.+)s");
	private static final Pattern CONJ_DISJ_PATTERN = Pattern.compile("conj_.+|disj_.+");
	
	/**
	 * Entry point for depth first search.
	 * @param struct
	 * @param parsedSB
	 * @param headParseStruct
	 * @param numSpaces
	 */
	public static void buildCommandsDfs(Struct struct, StringBuilder parsedSB, //ParseStruct headParseStruct, 
			int numSpaces, boolean printTiers, ParseState parseState) {
		structDeque = new ArrayList<Struct>();
		WLCommandList = new ArrayList<WLCommand>();
		
		dfs(struct, parsedSB, numSpaces, structDeque, WLCommandList, printTiers, parseState);
	}
	
	/**
	 * Get the triggered collection.
	 * @param struct
	 */
	private static Collection<ImmutableWLCommand> get_triggerCol(String triggerKeyWord, String triggerType){
		
		Collection<ImmutableWLCommand> triggeredSet;
		//copy into mutable collection. 
		//Note: using HashSet will introduce non-determinacy.		
		triggeredSet = new ArrayList<ImmutableWLCommand>(WLCommandMap.get(triggerKeyWord));
		
		//if(triggeredCol.isEmpty()){
		//add words from triggerWords redirects in case there's an entry there.
		//trigger word redirects: eg are -> is, since they are functionally equivalent
		if(triggerWordLookupMap.containsKey(triggerKeyWord)
				//Allow type to trigger entries in triggerWordLookupMap, e.g. type is "be"
				//|| triggerWordLookupMap.containsKey(triggerType)
				){
			Collection<String> col= triggerWordLookupMap.get(triggerKeyWord);
			for(String s : col){
				triggeredSet.addAll(WLCommandMap.get(s));
			}			
		}
			//look up using type
		if(triggeredSet.isEmpty() && WLCommandMap.containsKey(triggerType)){
			triggeredSet.addAll(WLCommandMap.get(triggerType));
		}
		
		Matcher pluralMatcher;
		if(triggeredSet.isEmpty() && (pluralMatcher = PLURAL_PATTERN.matcher(triggerKeyWord)).find() ){
			String keyWordSingular = pluralMatcher.group(1);
			triggeredSet.addAll(WLCommandMap.get(keyWordSingular));
		}

		if(triggeredSet.isEmpty() && triggerWordLookupMap.containsKey(triggerType)){			
			Collection<String> col= triggerWordLookupMap.get(triggerType);
			for(String s : col){
				triggeredSet.addAll(WLCommandMap.get(s));
			}			
		}
		return triggeredSet;
	}
	
	/**
	 * Match the slots in posTermList with Structs in structDeque.
	 * @param posTermList	posTermList for curCommand.
	 * @param triggerWordIndex	Index of the trigger word.
	 * @param usedStructsBool (Not really useful.)
	 * @param waitingStructList	Temporary list to add Struct's to.
	 * @return Whether the slots before triggerWordIndex have been satisfied.
	 */
	private static boolean findStructs(List<Struct> structDeque, WLCommand curCommand, 
			boolean[] usedStructsBool, List<Struct> waitingStructList){
		//System.out.println(" ******* structDeque " + structDeque);
		List<PosTerm> posTermList = WLCommand.posTermList(curCommand);
		int triggerWordIndex = WLCommand.triggerWordIndex(curCommand);
		//start from the word before the trigger word
		//iterate through posTermList
		//start index for next iteration of posTermListLoop		
		boolean curCommandSat = true;
		int structDequeStartIndex = structDeque.size() - 1;		
		
		posTermListLoop: for(int i = triggerWordIndex - 1; i > -1; i--){
			PosTerm curPosTerm = posTermList.get(i);			
			//auxilliary term
			if(curPosTerm.positionInMap() < 0) continue;
			
			WLCommandComponent curCommandComponent = curPosTerm.commandComponent();
			
			//int curStructDequeIndex = structDequeIndex;
			//iterate through Deque backwards
			//Iterator<Struct> dequeReverseIter = structDeque.descendingIterator();
			//int dequeIterCounter = structDeque.size() - 1;
			//int dequeIterCounter = structDequeStartIndex;
			
			for(int k = structDequeStartIndex; k > -1; k--){
			//for each struct in deque, go through list to match
			//Need a way to tell if all filled
				Struct curStructInDeque = structDeque.get(k);
				
				//avoid repeating this: 
				String nameStr = "";
				if(curStructInDeque.isStructA() && curStructInDeque.prev1NodeType().equals(NodeType.STR)){
					nameStr = curStructInDeque.prev1().toString();
				}else if(!curStructInDeque.isStructA()){
					nameStr = curStructInDeque.struct().get("name");
				}
				
				String curStructInDequeType = CONJ_DISJ_PATTERN.matcher(curStructInDeque.type()).find() ?
						//curStructInDeque.type().matches("conj_.+|disj_.+") ?
						curStructInDeque.type().split("_")[1] : curStructInDeque.type();

						if(WLCommand.disqualifyCommand(curStructInDequeType, curCommand.commandsCountMap())
								){
								//|| curStructInDequeType.equals("verbphrase")){
							return false;
						}
						
				if(//curStructInDequeType.matches(curCommandComponent.posStr())						
						curCommandComponent.getPosPattern().matcher(curStructInDequeType).find()
						&& curCommandComponent.getNamePattern().matcher(nameStr).find()
						//&& nameStr.matches(curCommandComponent.nameStr())
						&& !usedStructsBool[k] ){
					//see if name matches, if match, move on, continue outer loop
					
					Struct structToAdd = curStructInDeque;
					Struct curStructInDequeParent = curStructInDeque.parentStruct();
					
					while(curStructInDequeParent != null){
						
						String parentType = CONJ_DISJ_PATTERN.matcher(curStructInDeque.type()).find() ?
								//curStructInDequeParent.type().matches("conj_.+|disj_.+") ?
								curStructInDequeParent.type().split("_")[1] : curStructInDequeParent.type();
								
						String componentType = curCommandComponent.posStr();
						String parentNameStr = "";
						if(curStructInDequeParent.isStructA() && curStructInDequeParent.prev1NodeType().equals(NodeType.STR)){
							parentNameStr = (String)curStructInDequeParent.prev1();
						}else if(!curStructInDequeParent.isStructA() ){
							parentNameStr = curStructInDequeParent.struct().get("name");
						}
						//should match both type and term. Get parent of struct, e.g. "log of f is g" should get all of
						//"log of f", instead of just "f". I.e. get all of StructH.
						
						//System.out.println("\n^^^^^^^^" + ".name(): " + curCommandComponent.name() + " parentStr: " + parentNameStr+" type " +
						//componentType + " parentType " + parentType);						
						if(curCommandComponent.getNamePattern().matcher(parentNameStr).find()								
								//parentNameStr.matches(curCommandComponent.nameStr()) 
								&& parentType.matches(componentType)){
							structToAdd = curStructInDequeParent;
							curStructInDequeParent = curStructInDequeParent.parentStruct();
						}else{
							if(WLCommand.disqualifyCommand(parentType, curCommand.commandsCountMap())){
								return false;
							}
							break;
						}
					}
					
					//add struct to the matching Component if found a match							
					//System.out.println("-----ADDING struct " + structToAdd + " for command " + curCommand);
					waitingStructList.add(structToAdd);
					
					//set headStruct to structToAdd if it is closer to root
					/*if(WLCommand.headStruct(curCommand).dfsDepth() > structToAdd.dfsDepth()){
						WLCommand.set_headStruct(curCommand, structToAdd);
					}*/
					
					//usedStructsBool[dequeIterCounter] = true;
					usedStructsBool[k] = true;
					//is earlier than k-1 if parent added instead. Need to know parent's index
					structDequeStartIndex = k - 1;
					continue posTermListLoop;
				}
				//no match, see if command should be disqualified, to prevent overshooting
				//commands, i.e. encounter components of the command that has already been satisfied
				//such as "is".
				else{
					if(WLCommand.disqualifyCommand(curStructInDequeType, curCommand.commandsCountMap())){
						return false;
					}
				}
				//dequeIterCounter--;
			}
			curCommandSat = false;
			//done iterating through deque, but no match found; curCommand cannot be satisfied
			break;
		}
		
		if(logger.getLevel().equals(Level.INFO)){
			String msg = "---waitingStructList " + waitingStructList + " for command " + curCommand;
			System.out.println(msg);
			logger.info(msg);
		}
		
		return curCommandSat;
	}
	
	/**
	 * Searches through parse tree and matches with ParseStruct's.
	 * Builds tree of WLCommands.
	 * Convert to visitor pattern!
	 * 
	 * @param parseMap Map of String maps to which parseStructType.
	 * @param struct HeadStruct, 
	 * @param parsedSB StringBuilder. Don't actually need it here for now.
	 * @param headStruct the nearest ParseStruct that's collecting parses
	 * @param numSpaces is the number of spaces to print. Increment space if number is 
	 * @param structList List of Struct's collected so far, in dfs traversal order.
	 */
	private static void dfs(Struct struct, StringBuilder parsedSB, //ParseStruct headParseStruct, 
			int numSpaces, List<Struct> structList, List<WLCommand> WLCommandList, boolean printTiers,
			ParseState parseState) {
		//index used to keep track of where in Deque this stuct is
		//to pop off at correct index later
		//int structDequeIndex = structDeque.size();
		
		//list of commands satisfied at this level
		List<WLCommand> satisfiedCommandsList = new ArrayList<WLCommand>();;
		//add struct to all WLCommands in WLCommandList (triggered commands so far.)
		//check if satisfied. "is" is not added?!
		//Skip if immediate parents are conj or disj, i.e. already been added <--re-examine!!
		if (struct.parentStruct() == null 
				|| (struct.parentStruct() != null && !struct.parentStruct().type().matches("conj.*|disj.*"))) {

			//if(struct.nameStr().equals("field")) throw new IllegalStateException("*********"+ WLCommandList);
			Iterator<WLCommand> WLCommandListIter = WLCommandList.iterator();
			int WLCommandListSz = WLCommandList.size();
			for (int i = WLCommandListSz-1; i > -1; i--) {
				
				WLCommand curCommand = WLCommandListIter.next();
				curCommand = WLCommandList.get(i);
				boolean beforeTriggerIndex = false;
				//System.out.println("ADDING STRUCT " + struct + " for command " + curCommand);

				CommandSat commandSat = WLCommand.addComponent(curCommand, struct, beforeTriggerIndex);
				
				// if commandSat, remove all the waiting, triggered commands for
				// now, except current struct,
				// Use the one triggered first, which comes first in
				// WLCommandList.
				// But what if a longer WLCommand later fits better? Like
				// "derivative of f wrt x"
				//is no component added, means command already satisfied & built previously.
				if (commandSat.isCommandSat() && commandSat.isComponentAdded()) {	
					
					satisfiedCommandsList.add(curCommand);
					if(curCommand.getDefaultOptionalTermsCount() == 0
							|| !commandSat.hasOptionalTermsLeft() ){
						WLCommandListIter.remove();
					}
				}else if(commandSat.isDisqualified()){
					WLCommandListIter.remove();
					System.out.println("\n***COMMAND REMOVED. struct "+ struct );
				}
				
			}
		}		
		
		String triggerKeyWord = "";
		if (struct.isStructA() && struct.prev1NodeType().equals(NodeType.STR)) {
			triggerKeyWord = (String)struct.prev1();			
		}else if(!struct.isStructA()){
			triggerKeyWord = struct.struct().get("name");
		}
		
		String structType = struct.type();
		
		Matcher m = Pattern.compile("(?:conj_|disj_)(.*)").matcher(struct.type());
		if(m.find()){
			structType = m.group(1);
		}
		
		//if trigger a WLCommand, 
		boolean isTrigger = false;
		Collection<ImmutableWLCommand> triggeredCol = get_triggerCol(triggerKeyWord, structType);

		//use getSingular
		if(triggeredCol.isEmpty() && triggerKeyWord.length() > 2 
				&& triggerKeyWord.charAt(triggerKeyWord.length() - 1) == 's'){
			//need to write out all other cases, like ending in "es"
			String triggerWordSingular = triggerKeyWord.substring(0, triggerKeyWord.length() - 1);
			triggeredCol = get_triggerCol(triggerWordSingular, structType);				
		}
		
		if(!triggeredCol.isEmpty()){

			//is trigger, add all commands in list 
			//WLCommand curCommand;
			for(ImmutableWLCommand curCommandInCol : triggeredCol){
				//create mutable copy of the immutable command
				WLCommand curCommand = WLCommand.createMutableWLCommandCopy(curCommandInCol);
				
				//backtrack until either stop words (ones that trigger ParseStructs) are reached.
				//or the beginning of structDeque is reached.
				//or until commands prior to triggerWordIndex are filled.
				
				//whether terms prior to trigger word are satisfied
				//boolean beforeTriggerSat = true;
				//list of structs waiting to be inserted to curCommand via addComponent.
				//Temporary list instead of adding directly, since the terms prior need 
				//to be added backwards (always add at beginning), and list will not be 
				//added if !curCommandSat.
				//List<Struct> waitingStructList = new ArrayList<Struct>();
				//array of booleans to keep track of which deque Struct's have been used
				//boolean[] usedStructsBool = new boolean[structList.size()];

				//first set the headStruct to struct
				//WLCommand.set_headStruct(curCommand, struct);
				//match the slots in posTermList before the trigger term with Structs in structList.
				//Need to SKIP/rewire this (inefficient), just find and add at the same time!
				//beforeTriggerSat = findStructs(structList, curCommand, usedStructsBool, 
					//	waitingStructList);
				
				//curCommand's terms before trigger word are satisfied. Add them to triggered WLCommand.
				
					boolean curCommandSatWhole = false;
					//boolean hasOptionalTermsLeft = false;
					boolean isComponentAdded = false;
					boolean beforeTriggerSat = false;
					//add Struct corresponding to trigger word
					WLCommand.addTriggerComponent(curCommand, struct);
					
					CommandSat commandSat = null;

					for(int i = structList.size()-1; i > -1; i--){
						
						Struct curStruct = structList.get(i);
						//see if the whole command is satisfied, not just the part before trigger word
						//namely the trigger word is last word
						boolean beforeTrigger = true;
						//System.out.println("curCommandSatWhole*********"  +" "  + curStruct);
						//System.out.println("ADDING COMMAND (before) for STRUCT " + curStruct + " for Command + " + curCommand);
						commandSat = WLCommand.addComponent(curCommand, curStruct, beforeTrigger);
						
						curCommandSatWhole = commandSat.isCommandSat();		
						//System.out.println("curCommandSatWhole " + curCommandSatWhole);
						//hasOptionalTermsLeft = commandSat.hasOptionalTermsLeft();
						isComponentAdded |= commandSat.isComponentAdded();
						
						beforeTriggerSat = commandSat.beforeTriggerSat();
						if(beforeTriggerSat){
							break;
						}
					}					
					//System.out.println("*********COMMAND before trigger satisfied "+ beforeTriggerSat+ " " + curCommand);
					//if(commandSat != null){						
					if(beforeTriggerSat){
						//System.out.println("***-----------*got BEFORE as TRUE");
						if(curCommandSatWhole && isComponentAdded){							
							satisfiedCommandsList.add(curCommand);
						}else{							
							//if(!curCommandSatWhole || hasOptionalTermsLeft){
							WLCommandList.add(curCommand);
						}
					}
					/*}//trigger term was first term
					else{		
						
						WLCommandList.add(curCommand);						
					}*/
				
			}			
			isTrigger = true;			
		}
	
		structList.add(struct);
	
		// use visitor pattern!		
		if (struct.isStructA()) {
			//create ParseStruct's
			//the type T will depend on children. The type depends on struct's type
			//figure out types now, fill in later to ParseStruct later. 
			
			//ParseStruct curHeadParseStruct = headParseStruct;
			
			if(printTiers){
				System.out.print(struct.type());			
			}
			parsedSB.append(struct.type());
			
			if(printTiers) System.out.print("[");
			parsedSB.append("[");
			
			if (struct.prev1NodeType().isTypeStruct()) {
				
				//ParseStruct curHeadParseStruct = headParseStruct;
				//check if need to create new ParseStruct
				Struct prev1 = (Struct)struct.prev1();
				String prev1Type = prev1.type();
				ParseStructType parseStructType = ParseStructType.getType(prev1);
				boolean checkParseStructType = checkParseStructType(parseStructType);
				
				if(checkParseStructType){					
					
					numSpaces++;
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					//System.out.println(space);
					if(printTiers){ 
						System.out.print("\n " + space + prev1Type + ":>");					
					}
					parsedSB.append("\n " + space + prev1Type + ":>");
				}
				//set parent for this DFS path. The parent can change on each path!
				((Struct) struct.prev1()).set_parentStruct(struct);				
				//pass along headStruct, unless created new one here				
				dfs((Struct) struct.prev1(), parsedSB, numSpaces, structList, WLCommandList, printTiers,
						parseState);
				
				if(printTiers && checkParseStructType){
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					System.out.println(space);
				}
			}
			
			if (struct.prev2NodeType().isTypeStruct()) {
				
				if(printTiers) System.out.print(", ");
				parsedSB.append(", ");
				
				// avoid printing is[is], ie case when parent has same type as
				// child
				//String prev2Type = ((Struct)struct.prev2()).type();
				Struct prev2 = (Struct)struct.prev2();
				String prev2Type = prev2.type();
				ParseStructType parseStructType = ParseStructType.getType(prev2);
				//curHeadParseStruct = headParseStruct;
				
				//check if need to create new ParseStruct
				boolean checkParseStructType = checkParseStructType(parseStructType);
				if(checkParseStructType){
					/*curHeadParseStruct = new ParseStruct(parseStructType, prev2);
					headParseStruct.addToSubtree(parseStructType, curHeadParseStruct);
					curHeadParseStruct.set_parentParseStruct(headParseStruct); */
					
					numSpaces++;
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					if(printTiers) System.out.print("\n " + space + prev2Type + ":>");
					parsedSB.append("\n" + space + prev2Type + ":>");	
				}
				
				((Struct) struct.prev2()).set_parentStruct(struct);	

				dfs((Struct) struct.prev2(), parsedSB, numSpaces, structList, 
						WLCommandList, printTiers, parseState);
				if(printTiers && checkParseStructType){
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

			if (struct.prev1NodeType().equals(NodeType.STR)) {
				if(printTiers) System.out.print(struct.prev1());
				parsedSB.append(struct.prev1());
			}
			
			if (struct.prev2NodeType().equals(NodeType.STR)) {
				if (!struct.prev2().equals("")){
					if(printTiers) System.out.print(", ");
					parsedSB.append(", ");
				}
				if(printTiers) System.out.print(struct.prev2());
				parsedSB.append(struct.prev2());
			}

			if(printTiers) System.out.print("]");
			parsedSB.append("]");
			
			/*if(checkParseStructType0){
				String space = "";
				for(int i = 0; i < numSpaces; i++) space += " ";
				System.out.println(space);
			}*/
			//create new parseStruct to put in tree
			//if Struct (leaf) and not ParseStruct (overall head), done with subtree and return			
			
		}//if StructH 
		else {
			/*
			//set the head for each possibly-satisfied WLCommand in WLCommandList
			for(WLCommand command : WLCommandList){
				if(WLCommand.headStruct(command).dfsDepth() > struct.dfsDepth()){
					WLCommand.set_headStruct(command, struct);
				}
			}*/
			
			if(printTiers) System.out.print(struct.toString());
			parsedSB.append(struct.toString());

			List<Struct> children = struct.children();
			List<ChildRelation> childRelation = struct.childRelationList();

			//if (children == null || children.size() == 0)
				//return;
			if (children != null && children.size() != 0){
				
				if(printTiers) System.out.print("[");
				parsedSB.append("[");

			for (int i = 0; i < children.size(); i++) {
				if(printTiers) System.out.print(childRelation.get(i) + " ");
				parsedSB.append(childRelation.get(i) + " ");
				Struct ithChild = children.get(i);
				
				Struct childRelationStruct = new StructA<String, String>(childRelation.get(i).childRelation(), 
						NodeType.STR, "", NodeType.STR, "pre");
				childRelationStruct.set_parentStruct(struct);
				childRelationStruct.set_dfsDepth(struct.dfsDepth() + 1);
				
				//add child relation as Struct
				structList.add(childRelationStruct);
				//add struct to all WLCommands in WLCommandList
				//check if satisfied
				Iterator<WLCommand> ChildWLCommandListIter = WLCommandList.iterator();
				
				while(ChildWLCommandListIter.hasNext()){
					WLCommand curCommand = ChildWLCommandListIter.next();
					//System.out.println("ADDING COMMAND for STRUCT " + childRelationStruct +  " -curCommand " + curCommand);
					CommandSat commandSat = WLCommand.addComponent(curCommand, childRelationStruct, false);
					boolean isCommandSat = commandSat.isCommandSat();
					boolean hasOptionalTermsLeft = commandSat.hasOptionalTermsLeft();
					boolean isComponentAdded = commandSat.isComponentAdded();
					////add struct to posTerm to posTermList! ////////////
					
					if(isCommandSat && isComponentAdded){
						satisfiedCommandsList.add(curCommand);	
						
						if(curCommand.getDefaultOptionalTermsCount() == 0 || !hasOptionalTermsLeft){
							//need to remove from WLCommandList
							ChildWLCommandListIter.remove();
						}
					}else if(commandSat.isDisqualified()){
						ChildWLCommandListIter.remove();
						//System.out.println("\n***COMMAND REMOVED " + struct);
					}
					
				}
				
				ithChild.set_parentStruct(struct);
				dfs(ithChild, parsedSB, numSpaces, structList, WLCommandList, printTiers, parseState);
			}
			if(printTiers) System.out.print("]");
			parsedSB.append("]");
			}
		}
		
		//build the commands now after dfs into subtree
		for(WLCommand curCommand : satisfiedCommandsList){
			
			//set WLCommandStr in this Struct
			//need to find first Struct in posTermList
			/*List<PosTerm> posTermList = WLCommand.posTermList(curCommand);
			//to get the parent Struct of the first non-WL Struct
			
			int i = 0;
			while(posTermList.get(i).commandComponent().nameStr().matches("WL|AUX")) i++;
			
			PosTerm firstPosTerm = posTermList.get(i);
			//Struct posTermStruct = posTermList.get(0).posTermStruct(); 
			List<Struct> posTermStructList = WLCommand.getStructList(curCommand, firstPosTerm.commandComponent());
			//System.out.println("Satisfied command: " + curCommand);
			//System.out.println("First PosTerm" + firstPosTerm);
			
			
			//currently just get the first Struct in list, not canonical at all.			
			//firstPosTerm should have some Struct, as the command is satisfied.
			Struct posTermStruct = posTermStructList.get(0); */
			///////////
			/*Struct structToAppendCommandStr = posTermStruct;
			
			Struct parentStruct = posTermStruct.parentStruct();
			
			//go one level higher if parent exists
			Struct grandparentStruct = null;
			if(parentStruct != null) grandparentStruct = parentStruct.parentStruct();
			
			//set grandparent to parent if grandparent is a StructH
			structToAppendCommandStr = (grandparentStruct == null ? 
					(parentStruct == null ? structToAppendCommandStr : parentStruct) : 
						(grandparentStruct instanceof StructH ? parentStruct : grandparentStruct)); */
			////////
			try{
				WLCommand.build(curCommand, parseState);
			}catch(IllegalWLCommandStateException e){
				e.printStackTrace();
				logger.info(e.getStackTrace());
				continue;
			}
			//now append Str to wrapper inside build()
			//structToAppendCommandStr.append_WLCommandStr(curCommandString);
			
			//parentStruct.append_WLCommandStr(curCommandString);
			//System.out.println(curCommandString);
		}
		
	}
	
	/**
	 * Iterate through the WrapperList (list of potential commands) backwards, append the first encounter 
	 *	whose structsWithOtherHeadCount() lies above the set threshold.
	 *	Don't append if none exists.
	 *	Append all that has structsWithOtherHeadCount equal to the total 
	 *	component count, since those commands don't interfere with anything else.
	 * @param struct is Struct that the commandStr has been appended to, i.e. head struct.
	 * @param curParseStruct current parse struct used for collecting the parsed commands
	 * in this parse segment (punctuation-delimited). Store in parseStruct, to be sorted later,
	 * according to the mmap.
	 * @param contextVec is context vector for this WLCommand. 
	 * @param parsedSB WLCommand string builder, in form A\[Element] B
	 * @return whether contextVecConstructed
	 */
	private static boolean appendWLCommandStr(Struct struct, ParseStruct curParseStruct,
			StringBuilder parsedSB, Multimap<ParseStructType, ParsedPair> partsMap, 
			int[] contextVec, boolean contextVecConstructed, ParseState parseState){
		
		List<WLCommandWrapper> structWrapperList = struct.WLCommandWrapperList();
		int structWrapperListSz = structWrapperList.size();
		//ParseStruct curParseStruct = parseState.getCurParseStruct();
		
		//boolean contextVecConstructed = false;		
		//iterate backwards, so last-added ones (presumably longer span) come first
		//pick out the one with highest commandNumUnits.
		
		for(int i = structWrapperListSz - 1; i > -1; i--){
		//for(int i = 0; i < structWrapperListSz; i++){	
			WLCommandWrapper curWrapper = structWrapperList.get(i);
			WLCommand curCommand = curWrapper.wlCommand;
			
			//parent might have been used already, e.g. if A and B, parent of
			//A and B is "conj_..."
			boolean shouldAppendCommandStr = true;
			Struct parentStruct = struct.parentStruct();
			if(parentStruct != null){
				if(CONJ_DISJ_PATTERN.matcher(parentStruct.type()).find()
						&& parentStruct.WLCommandStrVisitedCount() > 0){
					shouldAppendCommandStr = false;
				}
			}
			//right now that threshold is: all components must be included.
			if(WLCommand.structsWithOtherHeadCount(curCommand) < 1
					&& shouldAppendCommandStr
					//&& struct.WLCommandStrVisitedCount() == 0
					){
				//System.out.println("wrapperList Size" + structWrapperListSz);
				//System.out.println(struct.WLCommandStr());
				//parsedSB.append(struct.WLCommandStr());
				parsedSB.append(curWrapper.wlCommandStr);
				
				//form the int[] contex vector by going down struct				
				// curCommand's type determines the num attached to head of structH,
				// e.g. "Exists[structH]" writes enum ParseRelation -2 at the index of structH	
				if(!contextVecConstructed){
					ParseTreeToVec.tree2vec(struct, contextVec, curWrapper);
					contextVecConstructed = true;
				}
				//get the ParseStruct based on type
				ParseStructType parseStructType = ParseStructType.getType(struct);
				//see if parent's type is "HYP or HYP_iff"
				//Struct parentStruct = struct.parentStruct();
				/*if(parentStruct != null){										
					ParseStructType parentParseStructType = ParseStructType.getType(parentStruct);
					Struct grandparentStruct = parentStruct.parentStruct();
					if(parentParseStructType.isHypType()){
						parseStructType = parentParseStructType;
					}//conj_assert[if[...], if[...]]
					else if(CONJ_DISJ_PATTERN.matcher(parentStruct.type()).find() 
							&& grandparentStruct != null){
						ParseStructType grandParentParseType = ParseStructType.getType(grandparentStruct);
						if(grandParentParseType.isHypType()){
							parseStructType = grandParentParseType;
						}
					}
				}*/
				ParsedPair pair = new ParsedPair(curWrapper.wlCommandStr, null, struct.maxDownPathScore(), 
						struct.numUnits(), WLCommand.commandNumUnits(curCommand), curCommand);
				//partsMap.put(type, curWrapper.WLCommandStr);	
				partsMap.put(parseStructType, pair);
				
				//determine whether to create new child ParseStruct, or add to current
				//layer
				/*if(checkParseStructType(parseStructType)){
					
					ParseStruct childParseStruct = new ParseStruct();
					//append child to headParseStruct
					curParseStruct.addToSubtree(parseStructType, childParseStruct);
					childParseStruct.set_parentParseStruct(curParseStruct);
					childParseStruct.addParseStructWrapper(parseStructType, curWrapper);
					//this struct already in hyp, don't create additional layers.
					//set the reference of the current struct to point to the newly created struct
					parseState.setCurParseStruct(childParseStruct);
					//System.out.println("curParseStruct " + childParseStruct);
					//throw new IllegalStateException("Created new struct!");
					
				}else{
					curParseStruct.addParseStructWrapper(parseStructType, curWrapper);					
				}*/
				//add to hold current command contained in curWrapper
				
				curParseStruct.addParseStructWrapper(parseStructType, curWrapper);
				//parseState.addParseStructWrapperPair(parseStructType, curWrapper);
				
				//System.out.println("partsMap being put in: " + curWrapper.WLCommandStr);
				break;
			}
		}
		return contextVecConstructed;
	}
	
	/**
	 * DFS for collecting the WLCommandStr's, instead of using the default 
	 * representations of the Struct's. To achieve a presentation that's closer
	 * to WL commands. Parse tree should have already finished building, with
	 * WLCommands attached.
	 * @param struct
	 * @param curParseStruct struct used to collect commands for this parse segment 
	 * (corresponding to one parse segment, could be multiple commands).
	 * @param parsedSB
	 */
	public static boolean collectCommandsDfs(Multimap<ParseStructType, ParsedPair> partsMap, ParseStruct curParseStruct,
			Struct struct, StringBuilder parsedSB, 
			int[] curStructContextVec, boolean shouldPrint, boolean contextVecConstructed,
			ParseState parseState) {
		//don't append if already incorporated into a higher command
		//System.out.print(struct.WLCommandStrVisitedCount());
		//WLComamnd() should not be null if WLCommandStr is not null
		//if(struct.WLCommandStr() != null && struct.WLCommandStrVisitedCount(k) < 1){	
		//whether context vec has been constructed
		//boolean contextVecConstructed = false;
		
		//ParseStruct curParseStruct = headParseStruct;
		/*if(struct.type().equals("assert")){
			System.out.println("^^^^^^" + struct + " " + struct.WLCommandStrVisitedCount());
		}*/
		
		if(struct.WLCommandWrapperList() != null && struct.WLCommandStrVisitedCount() < 1){	
		//if(struct.WLCommandWrapperList() != null){	
			/*if(WLCommand.structsWithOtherHeadCount(struct.WLCommand()) 
				> WLCommand.totalComponentCount(struct.WLCommand()) -2){
				//if(struct.WLCommandStr() != null ){
				parsedSB.append(struct.WLCommandStr());
			} */
			//collects the built WLCommand strings.
			contextVecConstructed = appendWLCommandStr(struct, curParseStruct, parsedSB, partsMap, curStructContextVec,
					contextVecConstructed, parseState);			
			
			shouldPrint = false;
			//reset WLCommandStr back to null, so next 
			//dfs path can create it from scratch
			//no need to do so as wrapper instances are created anew each dfs run
			//struct.clear_WLCommandStr();
			//nested commands should have some Struct in its posList 
			//that already contains sub nested commands' WLCommandStr.
			//return;
		} 
		
		/*if(!contextVecConstructed){
			ParseTreeToVec.tree2vec(struct, curStructContextVec, "");
		}*/
		
		if (struct.isStructA()) {
			
			if(shouldPrint) parsedSB.append(struct.type());			
			
			if(shouldPrint) parsedSB.append("[");
			
			if (struct.prev1NodeType().isTypeStruct()) {
				contextVecConstructed = collectCommandsDfs(partsMap, curParseStruct,
						(Struct) struct.prev1(), parsedSB, curStructContextVec, shouldPrint,
						contextVecConstructed, parseState);
			}

			// if(struct.prev2() != null && !struct.prev2().equals(""))
			// System.out.print(", ");
			if (((StructA<?, ?>) struct).prev2NodeType().isTypeStruct()) {
				// avoid printing is[is], ie case when parent has same type as
				// child
				if(shouldPrint) parsedSB.append(", ");
				contextVecConstructed = collectCommandsDfs(partsMap, curParseStruct, 
						(Struct) struct.prev2(), parsedSB, curStructContextVec, shouldPrint,
						contextVecConstructed, parseState);
			}

			if (struct.prev1NodeType().equals(NodeType.STR)) {
				if(shouldPrint) parsedSB.append(struct.prev1());
			}
			if (struct.prev2NodeType().equals(NodeType.STR)) {
				if (!struct.prev2().equals("")){
					if(shouldPrint) parsedSB.append(", ");
				}
				if(shouldPrint) parsedSB.append(struct.prev2());
			}

			if(shouldPrint) parsedSB.append("]");
		} else if (!struct.isStructA()) {

			if(shouldPrint) parsedSB.append(struct.toString());

			List<Struct> children = struct.children();
			List<ChildRelation> childRelation = struct.childRelationList();

			if (children == null || children.size() == 0){
				return contextVecConstructed;
			}
			
			if(shouldPrint) parsedSB.append("[");

			for (int i = 0; i < children.size(); i++) {
				if(shouldPrint) parsedSB.append(childRelation.get(i) + " ");

				contextVecConstructed = collectCommandsDfs(partsMap, curParseStruct,
						children.get(i), parsedSB, curStructContextVec, shouldPrint,
						contextVecConstructed, parseState);
			}
			if(shouldPrint) parsedSB.append("]");
		}
		return contextVecConstructed;
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
		struct.set_usedInOtherCommandComponent(false);
		
		if (struct.isStructA()) {
			
			if (struct.prev1NodeType().equals(NodeType.STRUCTA) 
					|| struct.prev1NodeType().equals(NodeType.STRUCTH) ) {
				dfsCleanUp((Struct) struct.prev1());
			}

			if (struct.prev2NodeType().isTypeStruct()) {				
				dfsCleanUp((Struct) struct.prev2());
			}
			
		} else {

			List<Struct> children = struct.children();
			
			if (children == null || children.size() == 0)
				return;
			
			for (int i = 0; i < children.size(); i++) {
				
				dfsCleanUp(children.get(i));
			}
		}
	}
	
	/**
	 * E.g. need to create new one if type is "HYP".
	 * @param type The enum ParseStructType
	 * @return whether to create new ParseStruct to parseStructHead
	 */
	static boolean checkParseStructType(ParseStructType type){
		if(type == ParseStructType.NONE || type == ParseStructType.STM){
			return false;
		}
		return true;
	}
	
	public static class WLCommandWrapper implements Serializable{
		
		private static final long serialVersionUID = 1L;

		/**
		 * Wraps around a WLCommand to put in list in each Struct,
		 * contains a WLCommand instance, and its index in list,
		 * in the order the commands are built: inner -> outer, earlier ->
		 * later.
		 */
		private WLCommand wlCommand;
		//WLCommand's index in list
		private int listIndex;
		//built command String associated with this command.
		private String wlCommandStr;
		//depth of highestStruct
		private int leastDepth;
		//highest struct in tree amongst Structs that build this WLCommand, ie closest to root.
		private Struct highestStruct;
		
		public WLCommandWrapper(WLCommand curCommand, int listIndex){			
			this.wlCommand = curCommand;
			this.listIndex = listIndex;
		}		
		
		public void set_leastDepth(int depth){
			this.leastDepth = depth;
		}
		
		public int leastDepth(){
			return this.leastDepth;
		}
		
		public void set_highestStruct(Struct struct){
			this.highestStruct = struct;
		}
		
		public Struct highestStruct(){
			return this.highestStruct;
		}
		
		//shouldn't need this
		public void set_listIndex(int index){
			this.listIndex = index;
		}
		
		public int listIndex(){
			return this.listIndex;
		}
		
		public WLCommand WLCommand(){
			return this.wlCommand;
		}
		
		public String WLCommandStr(){
			return this.wlCommandStr;
		}
		
		/**
		 * Why append and not just set??
		 * @param WLCommandStr
		 */
		public void set_WLCommandStr(StringBuilder WLCommandStr){
			//this.wlCommandStr = this.wlCommandStr == null ? "" : this.wlCommandStr; //<--now not necessary
			//System.out.println("&******&&&&&&&****** WLCommandStr before setting " + this.WLCommandStr);
			//Why append and not just set??
			//this.WLCommandStr += " " + WLCommandStr;
			//System.out.println("SETTING COMMAND STR " + WLCommandStr);
			this.wlCommandStr = WLCommandStr.toString();
		}		
		
		public void clear_WLCommandStr(){
			this.wlCommandStr = null;
		}
		
		@Override
		public String toString(){
			return "Wrapper around: " + this.wlCommand.toString();
		}
	}
}
