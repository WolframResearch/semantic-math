package thmp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.ParseToWLTree.WLCommandWrapper;
import thmp.Struct.NodeType;
import thmp.WLCommand.PosTerm;
import thmp.WLCommand.WLCommandComponent;

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
	
	/* commandsCountMap deliberately immutable, should not be modified during runtime */
	private Map<WLCommandComponent, Integer> commandsCountMap;
	//private String triggerWord; 
	//which WL expression to turn into using map components and how.
	//need to keep references to Structs in commandsMap
	// List of PosTerm with its position, {entsymb, 0}, {\[Element], -1}, {entsymb,2}
	//entsymb, 0, entsymb 1, use these to fulfill grammar rule
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
	 * Counter to keep track of how many Structs, with which the current Struct this WLCommand
	 * instance is associated to (has this struct as StructWLCommandStr), are associated with
	 * another head. If this number is above some threshold (e.g. > totalComponentCount-1), 
	 * do not use this WLCommand. Initial value is 0;
	 * Set to totalComponentCount *only* when WLCommand first copied.
	 */
	private int structsWithOtherHeadCount;
	
	/**
	 * Index of last added component inside posList.
	 * Starts as triggerIndex.
	 */
	private int lastAddedCompIndex;

	/**
	 * Wrapper for this WLCommand. Command & Wrapper should have 1-1 correspondence.
	 */
	private WLCommandWrapper commandWrapper;
	
	/**
	 * Number of optional terms in this command.
	 */
	private int optionalTermsCount;
	
	private int defaultOptionalTermsCount;
	
	/**
	 * Map of group numbers of optional terms and their terms counts, so values are number of terms
	 * needed in that group for it to be considered satisfied. Decremented at runtime
	 * (in copied WLCommand).
	 */
	private Map<Integer, Integer> optionalTermsGroupCountMap = new HashMap<Integer, Integer>();
	private static final String DEFAULT_AUX_NAME_STR = "AUX";
	private static final Pattern CONJ_DISJ_PATTERN = Pattern.compile("conj_.*|disj_.*");
	private static final Pattern DISQUALIFY_PATTERN = Pattern.compile("(if|If)");
	private static final String[] DISQUALIFY_STR_ARRAY = new String[]{"if", "If"};
	
	private static final boolean DEBUG = true;
	
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
	 * @return the optionalTermsGroupCountMap
	 */
	public Map<Integer, Integer> getOptionalTermsGroupCountMap() {
		return optionalTermsGroupCountMap;
	}

	/**
	 * @return the defaultOptionalTermsCount
	 */
	public int getDefaultOptionalTermsCount() {
		return defaultOptionalTermsCount;
	}

	/**
	 * @param optionalTermsCount the optionalTermsCount to set
	 */
	public void setOptionalTermsCount(int optionalTermsCount) {
		this.optionalTermsCount = optionalTermsCount;
	}
	
	/**
	 * @return the optionalTermsCount
	 */
	public int getOptionalTermsCount() {
		return optionalTermsCount;
	}

	/**
	 * @return the commandWrapper
	 */
	public Map<WLCommandComponent, Integer> commandsCountMap() {
		return this.commandsCountMap;
	}
	
	/**
	 * @return the commandWrapper
	 */
	public Multimap<WLCommandComponent, Struct> commandsMap() {
		return this.commandsMap;
	}
	
	/**
	 * @return the commandWrapper
	 */
	public WLCommandWrapper getCommandWrapper() {
		return commandWrapper;
	}

	/**
	 * @param commandWrapper the commandWrapper to set
	 */
	public void setCommandWrapper(WLCommandWrapper commandWrapper) {
		this.commandWrapper = commandWrapper;
	}

	/**
	 * The number of leaf nodes covered by this command.
	 * To be compared with numUnits of the structToAppendCommandStr.
	 * (Usually higher than that numUnits, since numUnits does not include
	 * # of children nodes of StructH's).
	 * The higher this number, the more spanning this command is.
	 * Again not intrinsic, depends on DFS path.
	 */
	private int commandNumUnits;	
	
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
	 * Immutable subclass .
	 * The mutable version created by WLCommand copies from this immutable version 
	 * every time a copy is needed. 
	 */
	public static class ImmutableWLCommand extends WLCommand{
		
		public ImmutableWLCommand(){
		}
		
		/**
		 * Static factory pattern.
		 * @param commands   Multimap of WLCommandComponent and the quantity needed for a WLCommand
		 * Also need posList
		 * @param commandsCountMap Immutable.
		 * @param posList
		 * @param componentCount
		 * @param triggerWordIndex
		 * @param optionalTermsCount
		 * @param optionalTermsMap Immutable. Could be null.
		 * @return
		 */
		private ImmutableWLCommand(Map<WLCommandComponent, Integer> commandsCountMap, 
				List<PosTerm> posList, int componentCount, int triggerWordIndex,
				int optionalTermsCount, Map<Integer, Integer> optionalTermsMap){
			
			//use setters instead!?
			super.commandsMap = ArrayListMultimap.create();	
			super.commandsCountMap = commandsCountMap;		
			super.posTermList = posList;
			super.componentCounter = componentCount;
			super.totalComponentCount = componentCount;
			
			super.triggerWordIndex = triggerWordIndex;
			super.lastAddedCompIndex = triggerWordIndex;
			super.optionalTermsCount = optionalTermsCount;
			super.defaultOptionalTermsCount = optionalTermsCount;
			super.optionalTermsGroupCountMap = optionalTermsMap;			
		}
		
		/**
		 * Builder for ImmutableWLCommand objects, to  
		 * enhance immutability.
		 */
		public static class Builder{
			//should be ok without modifiers , thus package-private
			Map<WLCommandComponent, Integer> commandsCountMap; 
			ImmutableList<PosTerm> posTermList; 
			
			//lastAddedCompIndex defaults to triggerWordIndex
			int triggerWordIndex;
			//defaultOptionalTermsCount defaults to optionalTermsCount
			int optionalTermsCount; 
			//componentCounter defaults to totalComponentCount
			int totalComponentCount;
			
			ImmutableMap<Integer, Integer> optionalTermsMap;
			ImmutableMap<Integer, Integer> optionalTermsGroupCountMap;
			
			public Builder(Map<WLCommandComponent, Integer> commandsCountMap, 
					ImmutableList<PosTerm> posList, int componentCount, int triggerWordIndex,
					int optionalTermsCount, ImmutableMap<Integer, Integer> optionalTermsMap){
				
				this.commandsCountMap = commandsCountMap;		
				this.posTermList = posList;
				this.totalComponentCount = componentCount;				
				this.triggerWordIndex = triggerWordIndex;
				this.optionalTermsCount = optionalTermsCount;
				this.optionalTermsGroupCountMap = optionalTermsMap;
			}
			
			public ImmutableWLCommand build(){
				return new ImmutableWLCommand(commandsCountMap, posTermList, totalComponentCount, triggerWordIndex, 
						optionalTermsCount, optionalTermsGroupCountMap);				
			}
			
		}
		
	}
	
	/**
	 * PosTerm stores a part of speech term, and the position in commandsMap
	 * it occurs, to build a WLCommand, ie turn triggered phrases into WL commands.
	 * 
	 */
	public static class PosTerm{
		/**
		 * CommandComponent 
		 */
		private WLCommandComponent commandComponent;
		
		/**
		 * Struct filling the current posTerm. Have to be careful eg for "of",
		 * which often is not a Struct itself, just part of a Struct.
		 * Could be null.
		 */
		private Struct posTermStruct;
		
		/**
		 * position of the relevant term inside a list in commandsMap.
		 * Ie the order it shows up in in the built-out command.
		 * -1 if it's a WL command, eg \[Element]. This should be set
		 * before building.
		 */
		private int positionInMap;

		/**
		 * Whether or not to include in the built String created by WLCommand.build()
		 */
		private boolean includeInBuiltString;
		
		/**
		 * Whether this term should be used to trigger TriggerMathObj system
		 */
		private boolean triggerMathObj;
		
		private boolean isTrigger;
		
		/**
		 * Builder class for PosTerm.
		 * Named PBuilder instead of PosTermBuilder, to 
		 */
		public static class PBuilder{
			//private static final int WLCOMMANDINDEX = WLCommandsList.WLCOMMANDINDEX;
			private static final int AUXINDEX = WLCommandsList.AUXINDEX;
			private static final int DEFAULT_POSITION_IN_MAP = WLCommandsList.getDefaultPositionInMap();
			private static final Pattern OPTIONAL_TOKEN_PATTERN = Pattern.compile("OPT(\\d*)");
			//private static final String TRIGGERMATHOBJSTR = "TriggerMathObj";				
			
			private WLCommandComponent commandComponent;			
			
			//private Struct posTermStruct;
			//this value is only used if it's set to a non-negative
			//value in one of the constructors
			private int positionInMap = DEFAULT_POSITION_IN_MAP;
			
			private boolean includeInBuiltString;	
			//whether or not to trigger math object inner product computation.
			private boolean isTriggerMathObj;
			//whether this term is the trigger term.
			private boolean isTrigger;			
			private boolean isOptionalTerm;
			//used to "untrigger" commands
			private boolean isNegativeTerm;
			//0 by default
			private int optionalGroupNum;
			
			/**
			 * @return the isTrigger
			 */
			public boolean isTrigger() {
				return this.isTrigger;
			}

			/**
			 * @return the positionInMap
			 */
			public int getPositionInMap() {
				return positionInMap;
			}

			/**
		     * need to expose this in PBuilder, to detect optional auxiliary terms
			 * @return whether this term is optional
			 */
			/*public boolean isOptionalTerm() {
				return isOptionalTerm;
			}*/			

			/**
			 * Builder for 4 terms.
			 * @param posStr
			 * @param nameStr
			 * @param includeInBuiltString
			 * @param isTrigger
			 */
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString){
				
				posStr = (null == posStr) ? ".*" : posStr.trim();
				
				// String nameStr = commandStrParts.length > 2 ?
				// commandStrParts[1] : "*";
				nameStr = (null == nameStr) ? ".*" : nameStr;
				
				//String[] cmdPart2Ar = commandStrParts[2].trim().split("_");
				
				//could be trigger_true, to indicate inclusion in posString
				//boolean useInPosList = cmdPart2Ar.length > 1 ? Boolean.valueOf(cmdPart2Ar[1])
						//: Boolean.valueOf(cmdPart2Ar[0]);	
				
				// process command and create WLCommandComponent and PosList
				this.commandComponent = new WLCommandComponent(posStr, nameStr);
				this.includeInBuiltString = includeInBuiltString;
			}
			
			/**
			 * 
			 * @param posStr Part of speech string
			 * @param nameStr
			 * @param includeInBuiltString
			 * @param isTrigger
			 * @param isTriggerMathObj
			 */
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString, boolean isTrigger,
					boolean isTriggerMathObj){
				this(posStr, nameStr, includeInBuiltString);
				this.isTrigger = isTrigger;
				this.isTriggerMathObj = isTriggerMathObj;
			}
			
			/**
			 * Builder for 4 terms or more, optional case.
			 * isTrigger must be false in this case, as trigger word is not optional.
			 * @param posStr
			 * @param nameStr
			 * @param includeInBuiltString true, with the understanding that only included
			 * in built String if entire group is satisfied.
			 * @param isTrigger
			 * @param isTriggerMathObj
			 * @param optionGroup  Which optional group this term belongs to.
			 */
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString,
					boolean isTriggerMathObj, String optionGroup){
				//isTrigger = false, as trigger word is not optional.
				this(posStr, nameStr, includeInBuiltString, false, isTriggerMathObj);
				
				int optionalGroupNum = 0;
				
				Matcher optionalTermMatcher = OPTIONAL_TOKEN_PATTERN.matcher(optionGroup);
				
				if(optionalTermMatcher.find()){
					String groupNum = optionalTermMatcher.group(1);
						//should only need to check if null, so why "" is no match?
					if(groupNum != null && !groupNum.equals("")){
							//System.out.println(groupNum + " groupNum");
						optionalGroupNum = Integer.valueOf(groupNum);
					}	
					this.optionalGroupNum = optionalGroupNum;
					this.isOptionalTerm = true;
				}					
			}
			
			/**
			 * Builder for 5 terms, with positionInMap (position of term in commandsMap).
			 * @param posStr
			 * @param nameStr
			 * @param includeInBuiltString
			 * @param isTrigger
			 * @param isTriggerMathObj
			 * @param optionGroup  Which optional group this term belongs to.
			 */
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString, boolean isTrigger,
					boolean isTriggerMathObj, int positionInMap){
				
				this(posStr, nameStr, includeInBuiltString, isTrigger, isTriggerMathObj);
				this.positionInMap = positionInMap;

			}
	
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString,
					boolean isTriggerMathObj, String optionGroup, int positionInMap){
				
				this(posStr, nameStr, includeInBuiltString, isTriggerMathObj, optionGroup);
				this.positionInMap = positionInMap;
			}
			
			/**
			 * Auxiliary Strings, e.g. brackets "[".
			 */
			public PBuilder(String posStr){				
				this.commandComponent = new WLCommandComponent(posStr, DEFAULT_AUX_NAME_STR);	
				this.positionInMap = AUXINDEX;
				this.includeInBuiltString = true;
			}
			
			/**
			 * Optional auxiliary Strings, i.e. those that go with optional
			 * rule components. Only included in built String if entire group
			 * is satisfied.
			 * @param optionGroup e.g. "OPT2"
			 */
			public PBuilder(String posStr, String optionGroup){				
				//includeInBuiltString = true; isTrigger = false;
				this(posStr, DEFAULT_AUX_NAME_STR, true,
						false, optionGroup, AUXINDEX);
				//this.commandComponent = new WLCommandComponent(posStr, DEFAULT_AUX_NAME_STR);	
				//this.positionInMap = AUXINDEX;
				
			}
			
			/**
			 * Construct negative terms, to untrigger commands.
			 * @param posStr
			 * @param type
			 */
			public PBuilder(String posStr, String nameStr, PosTermType type){	
				this(posStr, nameStr, false);
				if(type.equals(PosTermType.NEGATIVE)){					
					this.isNegativeTerm = true;
				}
			}
			
			/**
			 * Sets positionInMap, should be called before build'ing.
			 * @param positionInMap the positionInMap to set
			 */
			public void setPositionInMap(int positionInMap) {
				this.positionInMap = positionInMap;
			}
			
			/**
			 * @return the commandComponent
			 */
			public WLCommandComponent getCommandComponent() {
				return commandComponent;
			}

			/**
			 * Creates PosTerm from this PBuilder.
			 * @return
			 */
			public PosTerm build(){				
				
				if(this.isOptionalTerm){
					return new OptionalPosTerm(commandComponent, positionInMap, includeInBuiltString,
							isTriggerMathObj, optionalGroupNum);
				}else if(this.isNegativeTerm){
					return new NegativePosTerm(commandComponent, positionInMap);
				}else{
					return new PosTerm(commandComponent, positionInMap, includeInBuiltString,
							isTrigger, isTriggerMathObj);
				}
			}		
			
		}
		
		private PosTerm(WLCommandComponent commandComponent, int position, boolean includeInBuiltString,
				boolean isTrigger, boolean isTriggerMathObj){
			this.commandComponent = commandComponent;
			this.positionInMap = position;
			this.includeInBuiltString = includeInBuiltString;
			this.triggerMathObj = isTriggerMathObj;
			this.isTrigger = isTrigger;
		}
		
		/**
		 * @return the isOptionalTerm
		 */
		public boolean isTrigger() {
			return this.isTrigger;
		}
		
		/**
		 * @return the isOptionalTerm
		 */
		public boolean isOptionalTerm() {
			return false;
		}

		public boolean isNegativeTerm(){
			return false;
		}
		
		@Override
		public String toString(){
			return "{" + this.commandComponent + ", " + this.positionInMap + "}";
		}
		
		public WLCommandComponent commandComponent(){
			return this.commandComponent;
		}
		
		/**
		 * optionalGroupNum can only be called on optionalPosTerm.
		 * @return
		 */
		public int optionalGroupNum(){			
			throw new UnsupportedOperationException("Cannot call optionalGroupNum()"
					+ "on a non-optional PosTerm!");
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
	
	public static class NegativePosTerm extends PosTerm{
		
		public NegativePosTerm(WLCommandComponent commandComponent, int position){
			super(commandComponent, position, false, false, false);
			
		}
		
		@Override
		public boolean isNegativeTerm(){
			return true;
		}
	}
	
	public static class OptionalPosTerm extends PosTerm{

		/**
		 * The group number for this optional term.
		 */
		private int optionalGroupNum;
		
		public OptionalPosTerm(WLCommandComponent commandComponent, int position, boolean includeInBuiltString,
				boolean triggerMathObj, int optionalGroupNum) {
			//cannot be trigger if optional term
			super(commandComponent, position, includeInBuiltString, false, triggerMathObj);
			this.optionalGroupNum = optionalGroupNum;
			
		}
		
		/**
		 * @return the isOptionalTerm
		 */
		public boolean isOptionalTerm() {
			return true;
		}
		
		/**
		 * Get group number for this optional term.
		 * @return
		 */
		public int optionalGroupNum(){
			return optionalGroupNum;
		}
				
	}
	
	//build list of commands?
	//private String ; // 
	// compiled bits to command
	//private CommandBits commandBits;
	
	/**
	 * private constructor. Should be constructed using create().
	 */
	private WLCommand(){
	}
	
	/**
	 * Deep copy of the current WLCommand.
	 * Deliberately don't copy commandWrapper, as that's set depending on how command is used.
	 * @param curCommand To be copied
	 * @return Deep copy of curCommand
	 */
	public static WLCommand createMutableWLCommandCopy(ImmutableWLCommand curCommand){	
		
		WLCommand newCommand = new WLCommand();
		//trivial cast so the compiler can see the private fields of the superclass (WLCommand)
		//this trivial cast does not cost anything.
		newCommand.commandsMap = ArrayListMultimap.create(((WLCommand)curCommand).commandsMap);
		
		//commandsCountMap deliberately immutable, should not be modified during runtime
		newCommand.commandsCountMap = new HashMap<WLCommandComponent, Integer>(((WLCommand)curCommand).commandsCountMap) ;
		//newCommand.commandsCountMap = ((WLCommand)curCommand).commandsCountMap;
		
		//ImmutableMap.copyOf(curCommand.commandsCountMap);
		newCommand.posTermList = new ArrayList<PosTerm>(((WLCommand)curCommand).posTermList);
		
		newCommand.totalComponentCount = ((WLCommand)curCommand).totalComponentCount;
		newCommand.componentCounter = ((WLCommand)curCommand).totalComponentCount;

		newCommand.structsWithOtherHeadCount = 0;
		newCommand.lastAddedCompIndex = ((WLCommand) curCommand).triggerWordIndex;
		newCommand.triggerWordIndex = ((WLCommand) curCommand).triggerWordIndex;

		newCommand.optionalTermsCount = ((WLCommand) curCommand).optionalTermsCount;
		newCommand.defaultOptionalTermsCount = ((WLCommand)curCommand).optionalTermsCount;
		
		if(null != ((WLCommand)curCommand).optionalTermsGroupCountMap){
			newCommand.optionalTermsGroupCountMap 
				= new HashMap<Integer, Integer>(((WLCommand)curCommand).optionalTermsGroupCountMap);
			
		}
		return newCommand;
	}	
	/*
	 *super.commandsMap = ArrayListMultimap.create();	
			super.commandsCountMap = commandsCountMap;		
			super.posTermList = posList;
			super.componentCounter = componentCount;
			super.totalComponentCount = componentCount;
			
			super.triggerWordIndex = triggerWordIndex;
			super.lastAddedCompIndex = triggerWordIndex;
			super.optionalTermsCount = optionalTermsCount;
			super.defaultOptionalTermsCount = optionalTermsCount;
			super.optionalTermsGroupCountMap = optionalTermsMap; 
	 */
	
	/**
	 * Find struct with least depth amongst Structs that build this WLCommand
	 */
	private static Struct findCommandHead(ListMultimap<WLCommandComponent, Struct> commandsMap){
		Struct structToAppendCommandStr;
		//map to store Structs' parents. The integer can be left child (-1)
		//right child (1), or 0 (both children covered)
		Map<Struct, Integer> structIntMap = new HashMap<Struct, Integer>();
		int leastDepth = MAXDFSDEPTH;
		Struct highestStruct = null;
		
		for(Struct nextStruct : commandsMap.values()){
			//System.out.println("~~~nextStruct inside commandsMap " + nextStruct + " " + nextStruct.dfsDepth());
			
			//should never be null, commandsMap should be all filled
			if(nextStruct != null){
				Struct nextStructParent = nextStruct.parentStruct();
				Struct curStruct = nextStruct;
				//System.out.println("+++nextStructParent " + nextStructParent);
				while(nextStructParent != null){
					
					//System.out.println("***ParentPrev2" + nextStructParent.prev1() + " "  + nextStruct == nextStructParent.prev2());
					Integer whichChild = curStruct == nextStructParent.prev1() ? LEFTCHILD : 
						(curStruct == nextStructParent.prev2() ? RIGHTCHILD : NEITHERCHILD);
					
					if(structIntMap.containsKey(nextStructParent)){
						int existingChild = structIntMap.get(nextStructParent);
						if(nextStructParent instanceof StructA && existingChild != NEITHERCHILD
								&& whichChild != existingChild){
							//check if has left child, right child, or both.
							
							structIntMap.put(nextStructParent, BOTHCHILDREN);
							//update curStruct so to correctly determine which child parent is 
							curStruct = nextStructParent;
							//colored twice, need to put its parent in map
							nextStructParent = nextStructParent.parentStruct();
							
						}else{
							break;
						}
					}else if(whichChild != null){						
						structIntMap.put(nextStructParent, whichChild);
						curStruct = nextStructParent;
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
			//System.out.println("@@@Added Parent: " + nextStruct + " " + whichChild);
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
		
		//System.out.println("````LeastDepth " + leastDepth);

		//if head is ent (firstPosTermStruct.type().equals("ent") && ) and 
		//everything in this command belongs to or is a child of the head ent struct
		//if(highestStruct == firstPosTermStruct){
		structToAppendCommandStr = highestStruct;
			//System.out.println("~~~~~~~~~highestStruct"+highestStruct);
		/*}else{
			
			structToAppendCommandStr = firstPosTermStruct;
			Struct parentStruct = firstPosTermStruct.parentStruct();
		
		//go one level higher if parent exists
		Struct grandparentStruct = null;
		if(parentStruct != null) grandparentStruct = parentStruct.parentStruct();
		
		//set grandparent to parent if grandparent is a StructH
		structToAppendCommandStr = (grandparentStruct == null ? 
				(parentStruct == null ? structToAppendCommandStr : parentStruct) : 
					(grandparentStruct instanceof StructH ? parentStruct : grandparentStruct));
		}*/
		
		//System.out.println("structToAppendCommandStr" + structToAppendCommandStr);
		return structToAppendCommandStr;
	}
	
	/**
	 * Update the wrapper list to add current struct.
	 * @param nextStruct
	 * @param structToAppendCommandStr
	 * @return Whether nextStruct already has associated head.
	 */
	private static boolean updateWrapper(Struct nextStruct, Struct structToAppendCommandStr){
		
		//List<WLCommandWrapper> nextStructWrapperList = nextStruct.WLCommandWrapperList();
		Struct prevHeadStruct = nextStruct.structToAppendCommandStr();
		boolean prevStructHeaded = false; 
		
		if (prevHeadStruct != null) {
			List<WLCommandWrapper> prevHeadStructWrapperList = prevHeadStruct.WLCommandWrapperList();
			//no need to update structsWithOtherHeadCount if the heads are already same. Note the two
			//commands could be different, just with the same head.
			
			if(structToAppendCommandStr != prevHeadStruct && prevHeadStructWrapperList != null){
				// in this case structToAppendCommandStr should not be
				// null either				
				int wrapperListSz = prevHeadStructWrapperList.size();	
				//get the last-added command. <--should iterate and add count to all previous commands
				//with this wrapper? <--command building goes inside-out
				WLCommand lastWrapperCommand = prevHeadStructWrapperList.get(wrapperListSz-1).WLCommand();	
				// increment the headCount of the last wrapper object, should update every command's count!
				lastWrapperCommand.structsWithOtherHeadCount++;
				//System.out.println("Wrapper command struct " + headStruct);
				//System.out.println("***Wrapper Command to update: " + lastWrapperCommand);
				
			}
			prevStructHeaded = true;			
		}
		nextStruct.set_structToAppendCommandStr(structToAppendCommandStr);
		return prevStructHeaded;
	}
	
	/**
	 * Builds the WLCommand from commandsMap & posTermList after it's satisfied.
	 * Should be called after being satisfied. 
	 * @param curCommand command being built
	 * @param firstPosTermStruct Struct to append the built CommandStr to.
	 * Right now not using this struct value, just to set previousBuiltStruct to not be null.
	 * @return String form of the resulting WLCommand
	 */
	public static String build(WLCommand curCommand){
		
		if(curCommand.componentCounter > 0){ 
			return "";		
		}
		ListMultimap<WLCommandComponent, Struct> commandsMap = curCommand.commandsMap;
		//value is 0 if that group is satisfied
		Map<Integer, Integer> optionalTermsGroupCountMap = curCommand.optionalTermsGroupCountMap;
		
		//counts should now be all 0
		Map<WLCommandComponent, Integer> commandsCountMap = curCommand.commandsCountMap;
		List<PosTerm> posTermList = curCommand.posTermList;
		//use StringBuilder!
		StringBuilder commandSB = new StringBuilder();
		//the latest Struct to be touched, for determining if an aux String should be displayed
		boolean prevStructHeaded = false;
	
		//Struct headStruct = curCommand.headStruct;
		//determine which head to attach this command to
		Struct structToAppendCommandStr = findCommandHead(commandsMap);
		
		for(PosTerm term : posTermList){
			
			if(term.isNegativeTerm()){
				continue;
			}
			
			if(!term.includeInBuiltString){ 				
				//set its head Struct to structToAppendCommandStr,
				// ***This appears to always be null!?!
				Struct nextStruct = term.posTermStruct;				
				//System.out.println("&&&posTermStruct " + nextStruct);
				//get WLCommandWrapperList
				if(nextStruct != null){
					if(updateWrapper(nextStruct, structToAppendCommandStr)){
						//curCommand.structsWithOtherHeadCount++;
					}
					
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
			//boolean isOptionalTerm = term.isOptionalTerm();
			
			String nextWord = "";			
			//-1 if WL command or auxilliary String
			if(positionInMap != WLCommandsList.AUXINDEX && positionInMap != WLCommandsList.WLCOMMANDINDEX){
				
				List<Struct> curCommandComponentList = commandsMap.get(commandComponent);
				if(positionInMap >= curCommandComponentList.size()){
					if(DEBUG){
						System.out.println("positionInMap: " + positionInMap +" list size: "+curCommandComponentList.size() +" Should not happen!");
						System.out.println("COMPONENT" + commandComponent);
						System.out.println("COMMAND" + commandsCountMap);
					}
					continue;
				}
				
				Struct nextStruct = curCommandComponentList.get(positionInMap);
				//prevStruct = nextStruct;				
				if(nextStruct.previousBuiltStruct() != null){ 
					//set to null for next parse dfs iteration
					//****don't need to set to null here, but 
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
					nextWord = TriggerMathObj3.get_mathObjFromStruct(nextStruct, curCommand);

					if(nextWord.equals("")){
						//already added numUnits to Struct above, don't do it again.
						nextWord = nextStruct.simpleToString(true, null);
						
					}
				}else{
					//System.out.println("^^^Triggering command " + curCommand);
					//takes into account pro, and the ent it should refer to
					nextWord = nextStruct.simpleToString(true, curCommand);

				}
				//simple way to present the Struct
				//set to the head struct the currently built command will be appended to
				nextStruct.set_previousBuiltStruct(structToAppendCommandStr);
				structToAppendCommandStr.set_posteriorBuiltStruct(nextStruct);				
				//check if been assigned to a different head
				//prevStructHeaded = updateWrapper(nextStruct, structToAppendCommandStr);
				updateWrapper(nextStruct, structToAppendCommandStr);
				
				/*if(nextStruct.structToAppendCommandStr() == null){						
					prevStructHeaded = false;
				}else{
					//already been assigned to a different head
					nextStruct.structToAppendCommandStr().WLCommand().structsWithOtherHeadCount--;									
				}
				nextStruct.set_structToAppendCommandStr(structToAppendCommandStr); */
				
			}//index indicating this is a WL command.
			else if(positionInMap == WLCommandsList.WLCOMMANDINDEX){
				//should change to use simpletoString from Struct
				nextWord = term.commandComponent.posStr;
				//in case of WLCommand eg \\[ELement]
				//this list should contain Structs that corresponds to a WLCommand
				List<Struct> curCommandComponentList = commandsMap.get(commandComponent);
				//set the previousBuiltStruct.
				//should have size > 0 always <--nope! if element is not a true WLCommand, like an auxilliary string
				if(curCommandComponentList.size() > 0){					
					Struct nextStruct = curCommandComponentList.get(0);
					updateWrapper(nextStruct, structToAppendCommandStr);
						//curCommand.structsWithOtherHeadCount++;
										
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
			} else {
				//auxilliary Strings inside a WLCommand, eg "[", "\[Element]"	
			
				nextWord = term.commandComponent.posStr;

				//System.out.print("nextWord : " + nextWord + "prevStruct: " + prevStructHeaded);
			}
			
			if(term.isOptionalTerm()){
				int optionalGroupNum = term.optionalGroupNum();
				
				if(0 == optionalTermsGroupCountMap.get(optionalGroupNum)){
					commandSB.append(nextWord + " ");
				}
			}else{			
				commandSB.append(nextWord + " ");
			}
		}
		
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		if(DEBUG){
			System.out.println("\n CUR COMMAND: " + curCommand + " ");
			System.out.print("BUILT COMMAND: " + commandSB);
			System.out.println("HEAD STRUCT: " + structToAppendCommandStr);
		}
		
		//make WLCommand refer to list of WLCommands rather than just one.
		//Wrapper used here during build().
		WLCommandWrapper curCommandWrapper = structToAppendCommandStr.add_WLCommandWrapper(curCommand);
		
		if(DEBUG){
			System.out.println("~~~structToAppendCommandStr to append wrapper: " + structToAppendCommandStr);
			System.out.println("curCommand just appended: " + curCommand);
		}
		//structToAppendCommandStr.set_WLCommand(curCommand);
		
		curCommandWrapper.set_highestStruct(structToAppendCommandStr);
		//why append and not just set??
		curCommandWrapper.set_WLCommandStr(commandSB);
		return commandSB.toString();
	}
	
	/**
	 * Contains data for different command satisfiability conditions.
	 */
	public static class CommandSat{
		private boolean isCommandSat;
		//whether has optional terms remainining
		private boolean hasOptionalTermsLeft;
		private boolean componentAdded;
		//whether current command has been disqualified, used
		//to prevent overshooting.
		private boolean disqualified;
		//satisfied before trigger
		private boolean beforeTriggerSat;
		
		/**
		 * @return the disqualified
		 */
		public boolean isDisqualified() {
			return disqualified;
		}

		public CommandSat(boolean isCommandSat, boolean hasOptionalTermsLeft, boolean componentAdded){
			this.isCommandSat = isCommandSat;
			this.hasOptionalTermsLeft = hasOptionalTermsLeft;
			this.componentAdded = componentAdded;
		}

		public CommandSat(boolean isCommandSat, boolean hasOptionalTermsLeft, boolean componentAdded,
				boolean beforeTriggerSat){
			this(isCommandSat, hasOptionalTermsLeft, componentAdded);
			this.beforeTriggerSat = beforeTriggerSat;
		}
		
		public boolean beforeTriggerSat(){
			return this.beforeTriggerSat;
		}
		
		public void setBeforeTriggerSatToTrue(){
			this.beforeTriggerSat = true;
		}
		
		/**
		 * Used to disqualify commands.
		 * @param disqualified
		 */
		public CommandSat(boolean disqualified){
			this.disqualified = disqualified;
		}
		
		/**
		 * @return the componentAdded
		 */
		public boolean isComponentAdded() {
			return componentAdded;
		}

		/**
		 * @return the isCommandSat
		 */
		public boolean isCommandSat() {
			return isCommandSat;
		}

		/**
		 * @return the hasOptionalTermsLeft
		 */
		public boolean hasOptionalTermsLeft() {
			return hasOptionalTermsLeft;
		}
	}
	
	/**
	 * Check if only trivial terms left in posTermList before curIndex.
	 * Auxiliary to addComponent.
	 * @param posTermList
	 * @param curIndex
	 * @return
	 */
	private static boolean onlyTrivialTermsBefore(List<PosTerm> posTermList, int curIndex){
		for(int i = curIndex; i > -1; i--){
			PosTerm posTerm = posTermList.get(i);
			if(!posTerm.isNegativeTerm() && !posTerm.isOptionalTerm() && posTerm.positionInMap() > -1){
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Adds new Struct to commandsMap.
	 * @param curCommand	WLCommand we are adding PosTerm to
	 * @param newSrtuct 	Pointer to a Struct
	 * @param before Whether before triggerTerm
	 * @return 				Whether the command is now satisfied
	 * 
	 * Add to commandsMap only if component is required as indicated by commandsCountMap.
	 * BUT: what if the Struct just added isn't the one needed? Keep adding.
	 * If the name could be several optional ones, eg "in" or "of", so use regex .match("in|of")
	 */
	public static CommandSat addComponent(WLCommand curCommand, Struct newStruct, boolean before){
		
		//be careful with type, could be conj_.
		String structPreType = newStruct.type();
		String structType = CONJ_DISJ_PATTERN.matcher(structPreType).find() ?
				structPreType.split("_")[1] : structPreType;			
				
		Map<WLCommandComponent, Integer> commandsCountMap = curCommand.commandsCountMap;
		if(disqualifyCommand(structType, commandsCountMap)){
			boolean disqualified = true;
			return new CommandSat(disqualified);
		}

		Multimap<WLCommandComponent, Struct> commandsMap = curCommand.commandsMap;
		
		String structName = !newStruct.isStructA() ? newStruct.struct().get("name") : 
			newStruct.prev1NodeType().equals(NodeType.STR) ? (String)newStruct.prev1() : "";
		//System.out.println("inside addComponent, newStruct: " + newStruct);
		//System.out.println("###COMPONENT commandsCountMap " + curCommand.commandsMap);
		//whether component has been added. Useful to avoid rebuilding commands 
		
		boolean componentAdded = false;
		
		//need to iterate through the keys of countMap instead of just getting, 
		//because .hashcode won't find it for us, should know precisely which index to add,
		// should not need to iterate through all componentEntries.
		// Keep track of index in posList of component added before, and go backwards if before,
		// forwards if after.
		//before the trigger word
		List<PosTerm> posTermList = curCommand.posTermList;
		int lastAddedComponentIndex = curCommand.lastAddedCompIndex;
		int triggerWordIndex = curCommand.triggerWordIndex;
		//could be null.
		Map<Integer, Integer> optionalTermsGroupCountMap = curCommand.optionalTermsGroupCountMap;
		
		WLCommandComponent commandComponent;
		
		//String commandComponentPosTerm;
		//String commandComponentName;
		int posTermListSz =  posTermList.size();
		
		int i = lastAddedComponentIndex;
		//short-circuit if trigger is the first nontrivial term.
		if(before && onlyTrivialTermsBefore(posTermList, i-1)){
			
			return new CommandSat(false, curCommand.optionalTermsCount > 0, false, true);
		}
		
		//if the first time we add a component that's after triggerWordIndex.
		if(!before && lastAddedComponentIndex < triggerWordIndex){			
			i = triggerWordIndex;
		}
		
		//if(lastAddedComponentIndex != curCommand.triggerWordIndex){
		
		//determine what the next-to-be-added commandComponent is.
		if(before){			
			i--;
			//if auxilliary terms, eg "["
			while(i > -1 && posTermList.get(i).positionInMap < 0) i--;			
		}else{
			i++;
			while(i < posTermListSz && posTermList.get(i).positionInMap < 0) i++;		
		}
		
		if(i == posTermListSz || i < 0){
			
			boolean hasOptionalTermsLeft = (curCommand.optionalTermsCount > 0);
			return new CommandSat(curCommand.componentCounter < 1, hasOptionalTermsLeft, componentAdded);
		}
		
		
		PosTerm curPosTerm = posTermList.get(i);
		commandComponent = curPosTerm.commandComponent;
		boolean isOptionalTerm = curPosTerm.isOptionalTerm();
		int posTermPositionInMap = curPosTerm.positionInMap;
		//use pattern!
		//commandComponentPosTerm = commandComponent.posStr;
		//commandComponentName = commandComponent.nameStr;
		Pattern commandComponentPosPattern = commandComponent.getPosPattern();
		Pattern commandComponentNamePattern = commandComponent.getNamePattern();
		
		while(( (!commandComponentPosPattern.matcher(structType).find() || !commandComponentNamePattern.matcher(structName).find())
				//!structType.matches(commandComponentPosTerm) || !structName.matches(commandComponentName)) 
				&& (isOptionalTerm || curPosTerm.isNegativeTerm())
				/* or auxilliary term*/
				|| posTermPositionInMap < 0)
				/* ensure index within bounds*/
				//&& i < posTermListSz - 1 
				){
			
			if(before){
				if(i < 0){
					break;
				}
				i--;
			}else{
				if(i > posTermListSz - 1){
					break;
				}
				i++;
			}
			
			curPosTerm = posTermList.get(i);
			
			commandComponent = curPosTerm.commandComponent;
			isOptionalTerm = curPosTerm.isOptionalTerm();
			posTermPositionInMap = curPosTerm.positionInMap;
			
			commandComponentPosPattern = commandComponent.posPattern;
			commandComponentNamePattern = commandComponent.namePattern;			
			//commandComponentPosTerm = commandComponent.posStr;
			//commandComponentName = commandComponent.nameStr;
		
		}
		
		//System.out.println("GOT HERE****** newStruct " + newStruct);

		//int addedComponentsColSz = commandsMap.get(commandComponent).size();
		
		//disqualify term if negative term triggered
		if(curPosTerm.isNegativeTerm() 
				&& commandComponentPosPattern.matcher(structType).find()
				&& commandComponentNamePattern.matcher(structName).find()){
			boolean disqualified = true;
			return new CommandSat(disqualified);
		}
		
		int commandComponentCount = commandsCountMap.get(commandComponent);
		
		if(commandComponentPosPattern.matcher(structType).find()
				//structType.matches(commandComponentPosTerm) 	
				&& commandComponentNamePattern.matcher(structName).find()
				//&& structName.matches(commandComponentName)
				//this component is actually needed
				&& commandsCountMap.get(commandComponent) > 0
				//&& addedComponentsColSz < commandComponentCount
				){
			//System.out.println("#####inside addComponent, newStruct: " + newStruct);
			
			//check for parent, see if has same type & name etc, if going backwards.
			if(before){
				newStruct = findMatchingParent(commandComponentPosPattern, commandComponentNamePattern, newStruct);
			}
			
			commandsMap.put(commandComponent, newStruct);
			//sets the posTermStruct for the posTerm. No effect?!?***
			posTermList.get(i).set_posTermStruct(newStruct);
			//here newComponent must have been in the original required set
			commandsCountMap.put(commandComponent, commandComponentCount - 1);
			
			if(!newStruct.isStructA()){
				newStruct.set_usedInOtherCommandComponent(true);
			}
			//use counter to track whether map is satisfied
			if(!isOptionalTerm){
				curCommand.componentCounter--;
			}else{
				curCommand.optionalTermsCount--;
				
				int optionalGroupNum = curPosTerm.optionalGroupNum();
				//decrement optional terms
				//optionalTermsGroupCountMap cannot be null if grammar rules are valid.
				assert(null != optionalTermsGroupCountMap);
				
				Integer curCount = optionalTermsGroupCountMap.get(optionalGroupNum);
				if(null != curCount){
					optionalTermsGroupCountMap.put(optionalGroupNum, curCount-1);
				}
			}
			increment_commandNumUnits(curCommand, newStruct);
			curCommand.lastAddedCompIndex = i;
			componentAdded = true;			
			boolean hasOptionalTermsLeft = (curCommand.optionalTermsCount > 0);
			
			CommandSat commandSat = 
					new CommandSat(curCommand.componentCounter < 1, hasOptionalTermsLeft, componentAdded);
			
			if(before && onlyTrivialTermsBefore(posTermList, i-1)){
				commandSat.setBeforeTriggerSatToTrue();
			}
			
			return commandSat;
			
		} else {
			
			/*if(disqualifyCommand(commandComponent, commandComponentPosPattern, commandsCountMap, commandsMap)){
				boolean disqualified = true;
				return new CommandSat(disqualified);
			}*/

			//before trigger, and non-optional component not found, so command before trigger 
			//cannot be satisfied
			/*if(i < 0 && before && !isOptionalTerm){
				boolean disqualified = true;
				return new CommandSat(disqualified);
			}*/
			/*System.out.println("Component not matching inside addComponent");
			System.out.println("curCommand" + curCommand);
			System.out.println("commandComponentName" + commandComponentName);
			System.out.println("commandComponentPosTerm" + commandComponentPosTerm);
			System.out.println("structName" + structName);
			System.out.println("BEFORE? " + before); */
			boolean hasOptionalTermsLeft = (curCommand.optionalTermsCount > 0);
			return new CommandSat(curCommand.componentCounter < 1, hasOptionalTermsLeft, componentAdded);
		}
		//}
		//System.out.println("~~~commandComponentName" + commandComponentName);
		/*
		for(Entry<WLCommandComponent, Integer> commandComponentEntry : curCommand.commandsCountMap.entrySet()){
			commandComponent = commandComponentEntry.getKey();
			int commandComponentCount = commandComponentEntry.getValue();
			commandComponentPosTerm = commandComponent.posTerm;
			commandComponentName = commandComponent.name;
			
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
		*/
	}

	/**
	 * Find parent/ancestors if they satisfy same type/name requirements, if going backwards
	 * through the parse tree in dfs traversal order.
	 * @param componentPosPattern
	 * @param componentNamePattern
	 * @param struct
	 * @return
	 */
	private static Struct findMatchingParent(Pattern componentPosPattern, Pattern componentNamePattern,
			Struct struct){
		
		Struct structToAdd = struct;
		Struct structParent = struct.parentStruct();
		
		while(structParent != null){
			
			String structParentType = structParent.type();
			String parentType = CONJ_DISJ_PATTERN.matcher(structParentType).find() ?
					//curStructInDequeParent.type().matches("conj_.+|disj_.+") ?
					structParentType.split("_")[1] : structParentType;
					
			String parentNameStr = "";
			
			if(structParent.isStructA()){
				if(structParent.prev1NodeType().equals(NodeType.STR)){
					parentNameStr = (String)structParent.prev1();
				}
			}else{
				parentNameStr = structParent.struct().get("name");
			}
			
			//should match both type and term. Get parent of struct, e.g. "log of f is g" should get all of
			//"log of f", instead of just "f". I.e. get all of StructH.
			
			//System.out.println("\n^^^^^^^^" + ".name(): " + curCommandComponent.name() + " parentStr: " + parentNameStr+" type " +
			//componentType + " parentType " + parentType);						
			if(componentNamePattern.matcher(parentNameStr).find()								
					//parentNameStr.matches(curCommandComponent.nameStr()) 
					&& componentPosPattern.matcher(parentType).find()){
				
				structToAdd = structParent;
				structParent = structParent.parentStruct();
			}else{
				break;
			}
		}
		return structToAdd;
	}
	
	/**
	 * disqualify curCommand if encountering additional components of a type that's been 
	 * satisfied for curCommand, to avoid overreaching commands, i.e. skip nontrivial terms
	 * to match commands too eagerly.
	 * This method is only called if the current struct does not match the next commandComponent.
	 * @param commandComponent
	 * @param commandComponentPosPattern
	 * @param commandsCountMap
	 * @param commandsMap
	 */
	public static boolean disqualifyCommand(String structPosStr,
			Map<WLCommandComponent, Integer> commandsCountMap) {
		
		//if structPosStr is of a type that likely interrupts a command, e.g. "if"
		//Need to be smarter about "and". But only if these commands don't 
		//appear as commandComponents down the road.
		//the disqualify word triggered
		String disqualifyPos = null;
		//Set<String> disqualifyTriggerSet = new HashSet<String>();
		for(String pos : DISQUALIFY_STR_ARRAY){
			if(pos.equals(structPosStr)){
				disqualifyPos = pos;
				break;
			}
		}
		
		Matcher disqualifyMatcher = DISQUALIFY_PATTERN.matcher(structPosStr);
		if(disqualifyMatcher.find()){			
			disqualifyPos = disqualifyMatcher.group(1);
		}
		
		//Or, need to iterate over commandsCountMap, discard command if nontrivial parts
		//e.g. "verb" have already been satisfied. Not worth making a separate 
		for(Map.Entry<WLCommandComponent, Integer> entry : commandsCountMap.entrySet()){
			
			WLCommandComponent component = entry.getKey();
			Pattern posPattern = component.posPattern;
			boolean isPosWildCard = component.isPosWildCard();
			
			//if pos matches, and this component has been satisfied already.
			if(posPattern.matcher(structPosStr).find() && entry.getValue() == 0){
				//additional restriction on pos so we don't abandon commands based on trivial
				//additionally encountered terms such as prepositions. Only disqualify
				//based on extraneous terms such as "if" or verbs.
				if(posPattern.matcher("verb").find() && !isPosWildCard){
					//System.out.println("DISQUALIFYING COMMAND. structPosStr " + structPosStr);
					return true;					
				}
			}
			if(disqualifyPos != null && posPattern.matcher(disqualifyPos).find() && !isPosWildCard /*not .* */){
				disqualifyPos = null;
			}
		}
		
		//disqualified 
		if(disqualifyPos != null){
			return true;
		}
		
		/*int addedComponentColSz = commandsMap.get(commandComponent).size();
		if(addedComponentColSz > 0 && addedComponentColSz == commandsCountMap.get(commandComponent)){
			System.out.println("^^^^^COMMAND REMOVED");
			//additional restriction so we don't abandon commands based on trivial
			//additionally encountered terms such as prepositions. Only disqualify
			//based on extraneous terms such as "if" or verbs.
			if(commandComponentPosPattern.matcher("verb").find()
					|| commandComponentPosPattern.matcher("vbs").find()){
				
				return true;
			}
		}*/
		return false;
	}

	/**
	 * Add Struct corresponding to trigger word to curCommand
	 * @param curCommand current command under consideration
	 * @param newStruct new Struct to be added
 	 */
	public static void addTriggerComponent(WLCommand curCommand, Struct newStruct){
		//System.out.println("Adding trigger component " + newStruct + " " + curCommand);
		
		WLCommandComponent commandComponent = curCommand.posTermList.get(curCommand.triggerWordIndex).commandComponent;
		int commandComponentCount = curCommand.commandsCountMap.get(commandComponent);
		
		curCommand.commandsMap.put(commandComponent, newStruct);
		//here newComponent must have been in the original required set
		curCommand.commandsCountMap.put(commandComponent, commandComponentCount - 1);
		//use counter to track whether map is satisfied
		curCommand.componentCounter--;
		increment_commandNumUnits(curCommand, newStruct);
	}
	
	/**
	 * Removes the struct from its corresponding Component list in commandsMap.
	 * Typically used when a command has been satisfied, and its structs should be
	 * removed from other WLCommands in WLCommandList in ParseToWLTree that are partially built.
	 * @param curCommand	WLCommand to be removed from.
	 * @param curStruct		Struct to be removed.
	 * @return		Whether newStruct is found and removed.
	 * This is *not* used right now
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
					//int commandComponentCount = curCommand.commandsCountMap.get(commandComponent);
					//curCommand.commandsCountMap.put(commandComponent, commandComponentCount + 1);
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
	 * Index of trigger word in posTermList.
	 * @return
	 */
	public static int triggerWordIndex(WLCommand curCommand){
		return curCommand.triggerWordIndex;
	}
	
	/**
	 * Counter to keep track of which Structs, with which the current Struct this WLCommand
	 * instance is associated to (has this struct as structToAppendWLCommandStr), are associated with
	 * another head. If this number is above some threshold (e.g. > totalComponentCount-1), 
	 * do not use this WLCommand.
	 * @param curCommand
	 * @return
	 */
	public static int structsWithOtherHeadCount(WLCommand curCommand){
		return curCommand.structsWithOtherHeadCount;
	}
	
	/*public static void increment_structsWithOtherHeadCount(WLCommand curCommand){
		 curCommand.structsWithOtherHeadCount++;
	}*/
	
	public static int totalComponentCount(WLCommand curCommand){
		return curCommand.totalComponentCount;
	}
	
	public static void set_totalComponentCount(WLCommand curCommand, int newCount){
		 curCommand.totalComponentCount = newCount;
	}
	
	public static int commandNumUnits(WLCommand curCommand){
		return curCommand.commandNumUnits;
	}
	
	public static void set_commandNumUnits(WLCommand curCommand, int commandNumUnits){
		 curCommand.commandNumUnits = commandNumUnits;
	}
	
	/**
	 * Increment the commandNumUnits by 1, if newStruct is a leaf node.
	 * @param curCommand
	 */
	public static void increment_commandNumUnits(WLCommand curCommand, Struct newStruct){
		//instanceof is slow!
		if(newStruct.prev1() instanceof String && !(newStruct.prev2() instanceof Struct) 
				|| newStruct instanceof StructH){
			curCommand.commandNumUnits++;
			
		}
	}
	
	/**
	 * 
	 * @param curCommand
	 * @param numUnits	Amount to increment numUnits by.
	 */
	public static void increment_commandNumUnits(WLCommand curCommand, int numUnits){
			curCommand.commandNumUnits += numUnits;
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
		return this.posTermList.toString();
	}
	
	/**
	 * 
	 */
	public static class WLCommandComponent{
		//types should be consistent with types in Map
		//eg ent, symb, pre, etc
		private String posStr;
		
		//pattern for posTerm
		private Pattern posPattern;
		
		//whether the regex for making posPattern 
		private boolean isPosWildCard;
		
		//eg "of". Regex expression to be matched, eg .* to match anything
		private String nameStr;
		
		Pattern namePattern;
		
		public WLCommandComponent(String posTerm, String name){
			
			this.posStr = posTerm;
			
			if(posTerm.equals(".*")){
				isPosWildCard = true;
			}
			
			this.nameStr = name;
			//default auxiliary terms should not be compiled, as
			//they are not meant to be compared with anything.
			if(!name.equals(DEFAULT_AUX_NAME_STR)){
				this.posPattern = Pattern.compile(posTerm);
				this.namePattern = Pattern.compile(name);
			}
		}		
		
		public boolean isPosWildCard(){
			return isPosWildCard;
		}
		
		/**
		 * @return the posPattern
		 */
		public Pattern getPosPattern() {
			if(posStr.equals(DEFAULT_AUX_NAME_STR)){
				throw new IllegalArgumentException("Auxiliary terms "
						+ "are not compiled into patterns!");
			}
			return posPattern;
		}

		/**
		 * @return the namePattern
		 */
		public Pattern getNamePattern() {
			if(posStr.equals(DEFAULT_AUX_NAME_STR)){
				throw new IllegalArgumentException("Auxiliary terms "
						+ "are not compiled into patterns!");
			}
			return namePattern;
		}
		
		public String posStr(){
			return this.posStr;
		}
		
		public String nameStr(){
			return this.nameStr;
		}
		
		@Override
		public String toString(){
			return "{" + this.posStr + ", " + this.nameStr + "}";
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
			if(!this.posStr.equals(other.posStr)) return false;
			if(!this.nameStr.equals(other.nameStr)) return false;
			
			return true;
		}
		
		@Override
		public int hashCode(){
			//this does not produce uniform distribution! Need to do some shifting
			int hashcode = this.posStr.hashCode();
			hashcode += 19 * hashcode + this.nameStr.hashCode();
			return hashcode;
		}
	}	
	
	/**
	 * Type of component, i.e. negative.
	 * 
	 */
	public enum PosTermType{
		//stop the command (untrigger) once encountered.
		NEGATIVE;
		
	}
	
}
