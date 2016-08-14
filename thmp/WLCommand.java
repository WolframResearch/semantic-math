package thmp;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;

import thmp.ParseToWLTree.WLCommandWrapper;

/**
 * WL command, containing the components that need to be hashed. 
 * For commands where multiple types could fit, e.g. ent or symb,
 * store those types in a List,  
 * 
 * Each type should have two components, 
 * Similar types: symb/ent/noun, verbs/vbs/verbphrases, 
 * 
 * e.g. Element of this group.
 * Use the trigger words to prepare list of potential matches,
 * To build a command, set the commandBits using the hashes of the commands. 
 * 
 * ********
 * Each command has bucket (nested class with (Linked)ListMultimap and counter) of commands,
 * every time the deque adds something, it gets added to all awaiting commands, when a command
 * gets fulfilled, it turns into a WL command and gets popped off the deque.
 * Commands should know when to substitute subcomands for components.
 * ListMultimap contains components, each component contains type (eg ent), and optional name (eg field)
 * eg (pre; of) <--ent_prep
 * Commands should call structMap rules instead of hardcoding patterns.
 * Have nested static Class for each command component, each component 
 * has type, name (which can be wildcard to indicate any name)
 * 
 * @author yihed
 *
 */

public class WLCommand {
	
	//bucket to keep track of components needed in this command
	private ListMultimap<WLCommandComponent, Struct> commandsMap;  // e.g. element
	//need to keep track how filled the bucket is
	//
	private Map<WLCommandComponent, Integer> commandsCountMap;
	//private String triggerWord; 
	//which WL expression to turn into using map components and how.
	//need to keep references to Structs in commandsMap
	// List of PosTerm with its position, {entsymb, 0}, {\[Element], -1}, {entsymb,2}
	//entsymb, 0, entsymb 1, use these to build grammar
	private List<PosTerm> posTermList;

	/**
	 * Index of trigger word in posTermList.
	 * (expand to list to include multiple trigger words?)
	 */
	private int triggerWordIndex;
	
	/**
	 * Track the number of components left in this WLCommand.
	 * Used to determine whether this WLCommand has all the commandComponents it needs yet.
	 * Command is satisfied if componentCounter is 0.
	 */
	private int componentCounter;
	
	/**
	 * Total number of Struct's that count towards final command.
	 * Equal to componentCounter, but does not go down.
	 */
	private int totalComponentCount;
	
	/**
	 * Counter to keep track of which Structs, with which the current Struct this WLCommand
	 * instance is associated to (has this struct as StructWLCommandStr), are associated with
	 * another head. If this number is > totalComponentCount/2, do not use this WLCommand.
	 * Only set to totalComponentCount when WLCommand first copied.
	 */
	private int structsWithOtherHeadCount;
	
	/**
	 * Index of last added component inside posList.
	 * Starts as triggerIndex.
	 */
	private int lastAddedCompIndex;

	/**
	 * Index in list of WLCommand in Struct that 
	 * *Not* intrinsic to a WLCommand instance, create custom wrapper to put around this?
	 */
	//private int s;
	
	/**
	 * Least common head of Structs used to build this command, will become structToAppendCommandStr.
	 * It is determined during dfs in ParseToWLTree.
	 */
	//private Struct headStruct;
	
	/**
	 * Max tree depth in DFS.
	 */
	private static final int MAXDFSDEPTH = 100;
	//to be used in structIntMap to find highest struct
	private static final int LEFTCHILD = -1;
	private static final int RIGHTCHILD = 1;
	private static final int BOTHCHILDREN = 0;
	private static final int NEITHERCHILD = 2;
	
	/**
	 * PosTerm stores a part of speech term, and the position in commandsMap
	 * it occurs, to build a WLCommand, ie turn triggered phrases into WL commands.
	 * 
	 */
	public static class PosTerm{
		/**
		 * posTerm can be the terms that are used in structMap, 
		 * eg ent, symb, entsymb (either ent or symb works), pre, etc
		 */
		private WLCommandComponent commandComponent;
		
		/**
		 * Struct filling the current posTerm. Have to be careful eg for "of",
		 * which often is not a Struct itself, just part of a Struct.
		 */
		private Struct posTermStruct;
		
		/**
		 * position of the relevant term inside a list in commandsMap.
		 * Ie the order it shows up in in the built-out command.
		 * -1 if it's a WL command, eg \[Element].
		 */
		private int positionInMap;
		
		/**
		 * Whether or not to include in the built String created by build()
		 */
		private boolean includeInBuiltString;
		/**
		 * Whether this term should be used to trigger TriggerMathObj system
		 */
		private boolean triggerMathObj;
		
		public PosTerm(WLCommandComponent commandComponent, int position, boolean includeInBuiltString,
				boolean triggerMathObj){
			this.commandComponent = commandComponent;
			this.positionInMap = position;
			this.includeInBuiltString = includeInBuiltString;
			this.triggerMathObj = triggerMathObj;
		}
		
		@Override
		public String toString(){
			return "{" + this.commandComponent + ", " + this.positionInMap + "}";
		}
		
		public WLCommandComponent commandComponent(){
			return this.commandComponent;
		}
		
		/**
		 * @return the Struct corresponding to this posTerm
		 */
		public Struct posTermStruct(){
			return this.posTermStruct;
		}
		
		/**
		 * Set posTermStruct
		 * @param posTermStruct
		 */
		public void set_posTermStruct(Struct posTermStruct){
			this.posTermStruct = posTermStruct;
		}
		
		public int positionInMap(){
			return this.positionInMap;
		}
		
		public boolean includeInBuiltString(){
			return this.includeInBuiltString;
		}
		
		public boolean triggerMathObj(){
			return this.triggerMathObj;
		}
	}
	
	//build list of commands?
	//private String ; // 
	// compiled bits to command
	//private CommandBits commandBits;
	
	//private constructor. Should be built using build()
	//
	private WLCommand(){
	}
	
	/**
	 * Static factory pattern.
	 * @param commands   Multimap of WLCommandComponent and the quantity needed for a WLCommand
	 * Also need posList
	 */
	public static WLCommand create(Map<WLCommandComponent, Integer> commandsCountMap, 
			List<PosTerm> posList, int componentCounter, int triggerWordIndex){
		//defensively copy?? Even though not external-facing
		WLCommand curCommand = new WLCommand();
		curCommand.commandsMap = ArrayListMultimap.create();	
		curCommand.commandsCountMap = commandsCountMap;		
		curCommand.posTermList = posList;
		curCommand.componentCounter = componentCounter;
		curCommand.totalComponentCount = componentCounter;
		
		curCommand.triggerWordIndex = triggerWordIndex;
		curCommand.lastAddedCompIndex = triggerWordIndex;
		return curCommand;
	}
	
	/**
	 * @param curCommand To be copied
	 * @return Deep copy of curCommand
	 */
	public static WLCommand copy(WLCommand curCommand){	
		WLCommand newCommand = new WLCommand();
		newCommand.commandsMap = ArrayListMultimap.create(curCommand.commandsMap) ;
		newCommand.commandsCountMap = new HashMap<WLCommandComponent, Integer>(curCommand.commandsCountMap) ; 
		//ImmutableMap.copyOf(curCommand.commandsCountMap);
		newCommand.posTermList = new ArrayList<PosTerm>(curCommand.posTermList);
		newCommand.componentCounter = curCommand.componentCounter;
		newCommand.triggerWordIndex = curCommand.triggerWordIndex;
		newCommand.totalComponentCount = curCommand.totalComponentCount;
		newCommand.structsWithOtherHeadCount = curCommand.totalComponentCount;
		newCommand.lastAddedCompIndex = curCommand.lastAddedCompIndex;
		return newCommand;
	}
	
	/**
	 * Find struct with least depth amongst Structs that build this WLCommand
	 */
	private static Struct findCommandHead(ListMultimap<WLCommandComponent, Struct> commandsMap, Struct firstPosTermStruct){
		Struct structToAppendCommandStr;
		//map to store Structs' parents. The integer can be left child (-1)
		//right child (1), or 0 (both children covered)
		Map<Struct, Integer> structIntMap = new HashMap<Struct, Integer>();
		int leastDepth = MAXDFSDEPTH;
		Struct highestStruct = null;
		
		for(Struct nextStruct : commandsMap.values()){
			System.out.println("~~~nextStruct inside commandsMap " + nextStruct + " " + nextStruct.dfsDepth());
			
			//should never be null, commandsMap should be all filled
			if(nextStruct != null){
				Struct nextStructParent = nextStruct.parentStruct();
				System.out.println("+++nextStructParent " + nextStructParent);
				while(nextStructParent != null){
					
					System.out.println("***ParentPrev2" + nextStructParent.prev1() + " "  + nextStruct == nextStructParent.prev2());
					Integer whichChild = nextStruct == nextStructParent.prev1() ? LEFTCHILD : 
						(nextStruct == nextStructParent.prev2() ? RIGHTCHILD : NEITHERCHILD);
					
					if(structIntMap.containsKey(nextStructParent)){
						int existingChild = structIntMap.get(nextStructParent);
						if(nextStructParent instanceof StructA && existingChild != NEITHERCHILD
								&& whichChild != existingChild){
							//check if has left child, right child, or both.
							
							structIntMap.put(nextStructParent, BOTHCHILDREN);
							//colored twice, need to put its parent in map
							nextStructParent = nextStructParent.parentStruct();
							
						}else{
							break;
						}
					}else if(whichChild != null){						
						structIntMap.put(nextStructParent, whichChild);
						nextStructParent = nextStructParent.parentStruct();
						//break;
					}					
				}
				
				/*Integer structCount = structIntMap.get(nextStruct);
				if(structCount != null){					
					structIntMap.put(nextStruct, structCount + 1);			
				}else{
					structIntMap.put(nextStruct, 1);
				}*/
			}			
		}

		for(Entry<Struct, Integer> entry : structIntMap.entrySet()){
			Integer whichChild = entry.getValue();
			Struct nextStruct = entry.getKey();
			System.out.println("@@@Added Parent: " + nextStruct + " " + whichChild);
			//if(whichChild == BOTHCHILDREN || whichChild == RIGHTCHILD){
			if(whichChild == BOTHCHILDREN){
				int nextStructDepth = nextStruct.dfsDepth();
				if(nextStructDepth < leastDepth){
					highestStruct = nextStruct;
					leastDepth = nextStructDepth;			
				}
			}
		}
		
		//can happen if in a chain of structH's. Not really necessary anymore.
		if(leastDepth == MAXDFSDEPTH){
			for(Struct nextStruct : commandsMap.values()){
				int nextStructDepth = nextStruct.dfsDepth();
				if(nextStructDepth < leastDepth){
					highestStruct = nextStruct;
					leastDepth = nextStructDepth;				
				}
			}
		}		
		//highestStruct = headStruct;
		//leastDepth = headStruct.dfsDepth();
		System.out.println("````LeastDepth " + leastDepth);

		//if head is ent (firstPosTermStruct.type().equals("ent") && ) and 
		//everything in this command belongs to or is a child of the head ent struct
		if(highestStruct == firstPosTermStruct){
			structToAppendCommandStr = highestStruct;
			//System.out.println("~~~~~~~~~highestStruct"+highestStruct);
		}else{
			
			structToAppendCommandStr = firstPosTermStruct;
			Struct parentStruct = firstPosTermStruct.parentStruct();
		
		//go one level higher if parent exists
		Struct grandparentStruct = null;
		if(parentStruct != null) grandparentStruct = parentStruct.parentStruct();
		
		//set grandparent to parent if grandparent is a StructH
		structToAppendCommandStr = (grandparentStruct == null ? 
				(parentStruct == null ? structToAppendCommandStr : parentStruct) : 
					(grandparentStruct instanceof StructH ? parentStruct : grandparentStruct));
		}
		//System.out.println("structToAppendCommandStr" + structToAppendCommandStr);
		return structToAppendCommandStr;
	}
	
	/**
	 * Update the wrapper list to incorporate current struct.
	 * @param nextStruct
	 * @param structToAppendCommandStr
	 * @return Whether nextStruct already has associated head.
	 */
	private static boolean updateWrapper(Struct nextStruct, Struct structToAppendCommandStr){
		
		//List<WLCommandWrapper> nextStructWrapperList = nextStruct.WLCommandWrapperList();
		Struct headStruct = nextStruct.structToAppendCommandStr();
		boolean prevStructHeaded = false; 
		if (headStruct != null) {
			// in this case structToAppendCommandStr should not be
			// null either
			//System.out.println("listSIZE" + nextStructWrapperList.size());
			//System.out.println("NEXTSTRUCT" + nextStruct);
			// set the headCount of the last wrapper object
			List<WLCommandWrapper> headStructWrapperList = headStruct.WLCommandWrapperList();
			int wrapperListSz = headStructWrapperList.size();			
			WLCommand lastWrapperCommand = headStructWrapperList.get(wrapperListSz-1).WLCommand();			
			lastWrapperCommand.structsWithOtherHeadCount--;
			System.out.println("Wrapper command struct" + headStruct);
			//System.out.println("Wrapper Command to update: " + lastWrapperCommand);
			prevStructHeaded = true;
		}
		nextStruct.set_structToAppendCommandStr(structToAppendCommandStr);
		return prevStructHeaded;
	}
	
	/**
	 * Builds the WLCommand from commandsMap & posTermList after it's satisfied.
	 * Should be called after being satisfied. 
	 * @param curCommand command being built
	 * @param structToAppendCommandStr Struct to append the built CommandStr to.
	 * Right now not using this struct value, just to set previousBuiltStruct to not be null.
	 * @return String form of the resulting WLCommand
	 */
	public static String build(WLCommand curCommand, Struct firstPosTermStruct){
		if(curCommand.componentCounter > 0) return "";
		ListMultimap<WLCommandComponent, Struct> commandsMap = curCommand.commandsMap;
		//counts should now be all 0
		Map<WLCommandComponent, Integer> commandsCountMap = curCommand.commandsCountMap;
		List<PosTerm> posTermList = curCommand.posTermList;
		//use StringBuilder!
		String commandString = "";
		//the latest Struct to be touched, for determining if an aux String should be displayed
		boolean prevStructHeaded = false;
	
		//Struct headStruct = curCommand.headStruct;
		//determine which head to attach this command to
		Struct structToAppendCommandStr = findCommandHead(commandsMap, firstPosTermStruct);
		
		for(PosTerm term : posTermList){
			
			if(!term.includeInBuiltString){ 				
				//set its head Struct to structToAppendCommandStr,
				Struct nextStruct = term.posTermStruct;				
				
				//get WLCommandWrapperList
				if(nextStruct != null){
					updateWrapper(nextStruct, structToAppendCommandStr);
					
				//if(nextStruct != null){
				//if != null, some Wrappers have been added, so already associated to some commands.
				/*if(nextStructWrapperList != null){						
					if(nextStruct.structToAppendCommandStr() == null){
						nextStruct.set_structToAppendCommandStr(structToAppendCommandStr);					
					}else{
						//already been assigned to a different head
						nextStruct.structToAppendCommandStr().WLCommand().structsWithOtherHeadCount--;
						nextStruct.set_structToAppendCommandStr(structToAppendCommandStr);
					}
				} */
					// if != null, some Wrappers have been added, so already
					// associated to some commands.					
					
				}
				continue;
			}
			WLCommandComponent commandComponent = term.commandComponent;
			
			int positionInMap = term.positionInMap;
			
			String nextWord = "";			
			//-1 if WL command or auxilliary String
			if(positionInMap != WLCommandsList.AUXINDEX && positionInMap != WLCommandsList.WLCOMMANDINDEX){
				List<Struct> curCommandComponentList = commandsMap.get(commandComponent);
				if(positionInMap >= curCommandComponentList.size()){
					System.out.println("positionInMap: " + positionInMap +" list size: "+curCommandComponentList.size() +" Should not happen!");
					System.out.println("COMPONENT" + commandComponent);
					System.out.println("COMMAND" + curCommand);
					continue;
				}
				
				Struct nextStruct = curCommandComponentList.get(positionInMap);
				//prevStruct = nextStruct;				
				if(nextStruct.previousBuiltStruct() != null){ 
					//set to null for next parse dfs iteration
					//****it should be better to not set to null here, but 
					// set to null altogether after entire dfs iteration
					nextStruct.set_previousBuiltStruct(null);						
					//continue;	
				}
				
				/*if(nextStruct.posteriorBuiltStruct() != null){ 
					//set to null for next parse dfs iteration
					nextStruct.set_posteriorBuiltStruct(null);
					continue;					
				} */
				
				//check if need to trigger triggerMathObj
				if(term.triggerMathObj){
					//should check first if contains WLCommandStr, i.e. has been converted to some 
					//commands already
					nextWord = TriggerMathObj.get_mathObjFromStruct(nextStruct);
					
					if(nextWord.equals("")){
						nextWord = nextStruct.simpleToString(true);
					}
				}else{
					nextWord = nextStruct.simpleToString(true);
				}
				//simple way to present the Struct
				//set to the head struct the currently built command will be appended to
				nextStruct.set_previousBuiltStruct(structToAppendCommandStr);
				structToAppendCommandStr.set_posteriorBuiltStruct(nextStruct);				
				
				prevStructHeaded = updateWrapper(nextStruct, structToAppendCommandStr);
				
				/*if(nextStruct.structToAppendCommandStr() == null){						
					prevStructHeaded = false;
				}else{
					//already been assigned to a different head
					nextStruct.structToAppendCommandStr().WLCommand().structsWithOtherHeadCount--;									
				}
				nextStruct.set_structToAppendCommandStr(structToAppendCommandStr); */
				
			}else if(positionInMap == WLCommandsList.WLCOMMANDINDEX){
				//should change to use simpletoString from Struct
				nextWord = term.commandComponent.posTerm;
				
				//in case of WLCommand eg \\[ELement]
				//this list should contain Structs that corresponds to a WLCommand
				List<Struct> curCommandComponentList = commandsMap.get(commandComponent);
				//set the previousBuiltStruct.
				//should have size > 0 always <--nope! if element is not a true WLCommand, like an auxilliary string
				if(curCommandComponentList.size() > 0){					
					Struct nextStruct = curCommandComponentList.get(0);
					updateWrapper(nextStruct, structToAppendCommandStr);
					
					nextStruct.set_previousBuiltStruct(structToAppendCommandStr);
					structToAppendCommandStr.set_posteriorBuiltStruct(nextStruct);
					
					/*if(nextStruct.structToAppendCommandStr() == null){
						nextStruct.set_structToAppendCommandStr(structToAppendCommandStr);					
					}else{
						//already been assigned to a different head
						nextStruct.structToAppendCommandStr().WLCommand().structsWithOtherHeadCount--;
						nextStruct.set_structToAppendCommandStr(structToAppendCommandStr);
					} */
				}				
			}else {
				//if(prevStruct != null && prevStruct.structToAppendCommandStr() == null )
				//auxilliary Strings inside a WLCommand, eg "[", "\[Element]"	
				if(!prevStructHeaded){
					//nextWord = term.commandComponent.posTerm;
				}
				nextWord = term.commandComponent.posTerm;
				
				//System.out.print("nextWord : " + nextWord + "prevStruct: " + prevStructHeaded);
			}
			
			commandString += nextWord + " ";
		}
		System.out.println("\n CUR COMMAND: " + curCommand + " ");
		System.out.print("BUILT COMMAND: " + commandString);
		System.out.println("HEAD STRUCT: " + structToAppendCommandStr);
		
		//make WLCommand refer to list of WLCommands rather than just one.
		//Wrapper used here during build().
		WLCommandWrapper curCommandWrapper = structToAppendCommandStr.add_WLCommandWrapper(curCommand);
		System.out.println("~~~structToAppendCommandStr to append wrapper: " + structToAppendCommandStr);
		System.out.println("curCommand just appended: " + curCommand);
		//structToAppendCommandStr.set_WLCommand(curCommand);
		
		curCommandWrapper.set_highestStruct(structToAppendCommandStr);
		curCommandWrapper.append_WLCommandStr(commandString);
		return commandString;
	}
	
	/**
	 * Adds new Struct to commandsMap.
	 * @param curCommand	WLCommand we are adding PosTerm to
	 * @param newSrtuct 	Pointer to a Struct
	 * @return 				Whether the command is now satisfied
	 * 
	 * Add to commandsMap only if component is required as indicated by commandsCountMap.
	 * BUT: what if the Struct just added isn't the one needed? Keep adding.
	 * If the name could be several optional ones, eg "in" or "of", so use regex .match("in|of")
	 */
	public static boolean addComponent(WLCommand curCommand, Struct newStruct, boolean before){
		
		//if key.name .matches()
		//be careful with type, could be conj_, all sorts of stuff
		String structPreType = newStruct.type();
		String structType = structPreType.matches("conj_.*|disj_.*") ?
				structPreType.split("_")[1] : structPreType;
		
		String structName = newStruct instanceof StructH ? newStruct.struct().get("name") : 
			newStruct.prev1() instanceof String ? (String)newStruct.prev1() : "";
		
		//need to iterate through the keys of countMap instead of just getting, 
		//because .hashcode won't find it for us, should know precisely which index to add,
		// should not need to iterate through all componentEntries.
		// Keep track of index in posList of component added before, and go backwards if before,
		// forwards if after.
		//before the trigger word
		List<PosTerm> posList = curCommand.posTermList;
		int lastAddedComponentIndex = curCommand.lastAddedCompIndex;
		//
		WLCommandComponent commandComponent;
		String commandComponentPosTerm;
		String commandComponentName;
		//
		int i = lastAddedComponentIndex;
		if(lastAddedComponentIndex != curCommand.triggerWordIndex){
		if(before){			
			//i--;
			//if auxilliary terms, eg "["
			while(i > -1 && posList.get(i).positionInMap < 0) i--;			
		}else{
			i++;
			while(i < posList.size() && posList.get(i).positionInMap < 0) i++;		
		}
		//if no match, return
		commandComponent = posList.get(i).commandComponent;
		commandComponentPosTerm = commandComponent.posTerm;
		commandComponentName = commandComponent.name;
		
		if(!(structType.matches(commandComponentPosTerm) 				
				&& structName.matches(commandComponentName))){
			System.out.println("curCommand" + curCommand);
			System.out.println("commandComponentName" + commandComponentName);
			System.out.println("commandComponentPosTerm" + commandComponentPosTerm);
			System.out.println("structName" + structName);
			System.out.println("BEFORE? " + before);
			return false;
		}
		}
		//System.out.println("~~~commandComponentName" + commandComponentName);
		for(Entry<WLCommandComponent, Integer> commandComponentEntry : curCommand.commandsCountMap.entrySet()){
			commandComponent = commandComponentEntry.getKey();
			int commandComponentCount = commandComponentEntry.getValue();
			commandComponentPosTerm = commandComponent.posTerm;
			commandComponentName = commandComponent.name;
			
			//if WL expr matches, add Struct to that component, eg \[Element]
			/*if(commandComponentName.matches("WL.*") ){
				String[] nameAr = commandComponentName.split("-");
				if(nameAr.length > 1 && commandComponentCount > 0
						&& structName.matches(nameAr[1])){
					curCommand.commandsMap.put(commandComponent, newStruct);
					//here newComponent must have been in the original required set
					curCommand.commandsCountMap.put(commandComponent, commandComponentCount - 1);					
					break;
				}
			}					
			else */
			if(structType.matches(commandComponentPosTerm) 
					&& commandComponentCount > 0 
					&& structName.matches(commandComponentName)){
				//put commandComponent into commandsMap
				//if map doesn't contain newComponent, null !> 0						
				curCommand.commandsMap.put(commandComponent, newStruct);
				//here newComponent must have been in the original required set
				curCommand.commandsCountMap.put(commandComponent, commandComponentCount - 1);
				//use counter to track whether map is satisfied
				curCommand.componentCounter--;
				break;
			}//else if(commandComponent.posTerm.equals("WL") && commandComponentCount > 0){}
			
		}			
		
		//shouldn't be < 0!
		return curCommand.componentCounter < 1;
	}

	/**
	 * Add Struct corresponding to trigger word to curCommand
 	 */
	public static void addTriggerComponent(WLCommand curCommand, Struct newStruct){
		WLCommandComponent commandComponent = curCommand.posTermList.get(curCommand.triggerWordIndex).commandComponent;
		int commandComponentCount = curCommand.commandsCountMap.get(commandComponent);
		
		curCommand.commandsMap.put(commandComponent, newStruct);
		//here newComponent must have been in the original required set
		curCommand.commandsCountMap.put(commandComponent, commandComponentCount - 1);
		//use counter to track whether map is satisfied
		curCommand.componentCounter--;
	}
	
	/**
	 * Removes the struct from its corresponding Component list in commandsMap.
	 * Typically used when a command has been satisfied, and its structs should be
	 * removed from other WLCommands in WLCommandList in ParseToWLTree that are partially built.
	 * @param curCommand	WLCommand to be removed from.
	 * @param curStruct		Struct to be removed.
	 * @return		Whether newStruct is found and removed.
	 */
	public static boolean removeComponent(WLCommand curCommand, 
			Struct curStruct){
		//need to iterate through the keys of countMap instead of just getting, 
		//because .hashcode won't find it for us		
		for(WLCommandComponent commandComponent : curCommand.commandsMap.keySet()){
			
			List<Struct> structSet = curCommand.commandsMap.get(commandComponent);
			
			Iterator<Struct> structSetIter = structSet.iterator();
			
			while(structSetIter.hasNext()){
				Struct curComponentStruct = structSetIter.next();
				//only reference equality need to be checked, as it's always the reference to the 
				//particular Struct that's added
				if(curComponentStruct == curStruct){
					structSetIter.remove();
					curCommand.componentCounter++;
					int commandComponentCount = curCommand.commandsCountMap.get(commandComponent);
					curCommand.commandsCountMap.put(commandComponent, commandComponentCount + 1);
					return true;
				}
			}			
		}			
		return false;
	}
	
	/**
	 * Retrieves list of Structs from commandsMap with key component.
	 * @param component		key to retrieve List with
	 * @param curCommand	is current command
	 * @return List in commandsMap
	 */
	public static List<Struct> getStructList(WLCommand curCommand, WLCommandComponent component){
		return curCommand.commandsMap.get(component);
	}
	
	/**
	 * @param curCommand
	 * @return posTermList of current command
	 */
	public static List<PosTerm> posTermList(WLCommand curCommand){
		return curCommand.posTermList;
	}
	
	/**
	 * 
	 * @return
	 */
	public static int triggerWordIndex(WLCommand curCommand){
		return curCommand.triggerWordIndex;
	}
	
	public static int structsWithOtherHeadCount(WLCommand curCommand){
		return curCommand.structsWithOtherHeadCount;
	}
	
	public static void set_structsWithOtherHeadCount(WLCommand curCommand, int newCount){
		 curCommand.structsWithOtherHeadCount = newCount;
	}
	
	public static int totalComponentCount(WLCommand curCommand){
		return curCommand.totalComponentCount;
	}
	
	public static void set_totalComponentCount(WLCommand curCommand, int newCount){
		 curCommand.totalComponentCount = newCount;
	}
	
	/*public static Struct headStruct(WLCommand curCommand){
		return curCommand.headStruct;
	}

	public static void set_headStruct(WLCommand curCommand, Struct headStruct){
		curCommand.headStruct = headStruct;
	}*/
	
	/**
	 * @return Is this command (commandsMap) satisfied. 
	 * 
	 */
	public static boolean isSatisfied(WLCommand curCommand){
		//shouldn't be < 0!
		return curCommand.componentCounter < 1;
	}
	
	@Override
	public String toString(){
		return this.commandsCountMap.toString();
	}
	
	/**
	 * 
	 */
	public static class WLCommandComponent{
		//types should be consistent with types in Map
		//eg ent, symb, pre, etc
		private String posTerm;
		//eg "of". Regex expression to be matched, eg .* to match anything
		private String name;
		
		public WLCommandComponent(String posTerm, String name){
			this.posTerm = posTerm;
			this.name = name;
		}
		
		public String posTerm(){
			return this.posTerm;
		}
		
		public String name(){
			return this.name;
		}
		
		@Override
		public String toString(){
			return "{" + this.posTerm + ", " + this.name + "}";
		}
		
		/**
		 * 
		 */
		@Override
		public boolean equals(Object obj){
			if(!(obj instanceof WLCommandComponent)) return false;
			
			WLCommandComponent other = (WLCommandComponent)obj;
			//here comparing posTerm and name as Strings rather than 
			//matching as regexes; This is ok since the only times we 
			//look up we have the original Pos terms
			//should not be able to access   .posTerm directly!
			if(!this.posTerm.equals(other.posTerm)) return false;
			if(!this.name.equals(other.name)) return false;
			
			return true;
		}
		
		@Override
		public int hashCode(){
			//this does not produce uniform distribution! Need to do some shifting
			int hashcode = this.posTerm.hashCode();
			hashcode += 19 * hashcode + this.name.hashCode();
			return hashcode;
		}
	}
	
}
