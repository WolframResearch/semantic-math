package thmp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.wolfram.jlink.Expr;

import thmp.exceptions.IllegalWLCommandStateException;
import thmp.ParseToWLTree.WLCommandWrapper;
import thmp.RelationVec.RelationType;
import thmp.Struct.ChildRelationType;
import thmp.Struct.NodeType;
import thmp.WLCommand.PosTerm;
import thmp.WLCommand.WLCommandComponent;
import thmp.WLCommand.PosTerm.PosTermConnotation;
import thmp.utils.Buggy;
import thmp.utils.ExprUtils;

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

public class WLCommand implements Serializable{
	
	private static final long serialVersionUID = -1217116893288860792L;
	
	//word that would trigger this WLCommand
	private String triggerWord;
	
	//bucket to keep track of components needed in this command
	private ListMultimap<WLCommandComponent, Struct> commandsMap;  // e.g. element
	
	/* commandsCountMap deliberately immutable, should not be modified during runtime */
	private Map<WLCommandComponent, Integer> commandsCountMap;
	
	//List of commands that have been composited (absorbed) into the current one.
	//E.g. "If assert", and the assert picks up a command by itself.
	private List<WLCommand> composedWLCommandsList;
	
	//private String triggerWord; 
	//which WL expression to turn into using map components and how.
	//need to keep references to Structs in commandsMap
	// List of PosTerm with its position, {entsymb, 0}, {\[Element], -1}, {entsymb,2}
	//entsymb, 0, entsymb 1, use these to fulfill grammar rule
	private List<PosTerm> posTermList;
	/* Copied command with optional terms, 
	 * only applicable for WLCommands with optional terms. */
	private transient WLCommand copyWithOptTermsCommand;
	private transient WLCommand copyWithOutOptTermsCommand;
	//map of trigger terms focus on  
	private static final transient Map<String, String> negativeTriggerCommandsMap;
	
	/**
	 * Index of trigger word in posTermList.
	 * (expand to list to include multiple trigger words?)
	 */
	private int triggerWordIndex;
	
	/**
	 * Track the number of components left in this WLCommand.
	 * Used to determine whether this WLCommand has all the commandComponents it needs yet.
	 * Command is satisfied if componentCounter is 0. Does *NOT* count optional terms.
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
	/*Struct with other head, such that the struct is the head of the command itself. 
	 * This means we can use a built command when, even if 
	 * structsWithOtherHeadCount == 1, but the struct used is the head of the other command.
	 * E.g. If assertion, here assertion can be used in some other command, but the built assertion
	 * command can still be used in the bigger "if assert" command, because assertion is the head of
	 * the inner command built*/
	private Struct structHeadWithOtherHead;
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
	private static final Pattern CONJ_DISJ_PATTERN = Pattern.compile("conj_.+|disj_.+");
	private static final Pattern DISQUALIFY_PATTERN = Pattern.compile("(if|If|iff|Iff)");
	private static final String[] DISQUALIFY_STR_ARRAY = new String[]{"if", "If", "if", "Iff"};
	
	private static final boolean DEBUG = true;
	private static final Logger logger = LogManager.getLogger(WLCommand.class);
	
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
	 * @return the triggerWord
	 */
	public String getTriggerWord() {
		return triggerWord;
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
	
	public void addComposedWLCommands(WLCommand composedCommand){
		this.composedWLCommandsList.add(composedCommand);
	}
	
	public List<WLCommand> composedWLCommandsList(){
		return this.composedWLCommandsList;
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
	 * The *higher* this number, the more spanning this command is. Hence better.
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
	// pattern used to detect negative terms
	private static final Pattern TRIGGER_WORD_NOT_PATTERN = Pattern.compile("^(?:.*not* .+|.+ no(?:t|$).*)$");
	//pattern to be replaced with the empty string, to turn the negative term into the positive corresponding part.
	protected static final Pattern NEGATIVE_TRIGGER_PATTERN = Pattern.compile("(?:does not\\s*|do not\\s*|\\s*not\\s*| no$)");
	
	static {	
		negativeTriggerCommandsMap = new HashMap<String, String>();
		negativeTriggerCommandsMap.put("~HasProperty~", " ~HasNotProperty~ ");
		negativeTriggerCommandsMap.put(" ~HasProperty~ ", " ~HasNotProperty~ ");
		negativeTriggerCommandsMap.put(" ~HasProperty~ {", " ~HasNotProperty~ {");
	}
	/**
	 * Immutable subclass .
	 * The mutable version created by WLCommand copies from this immutable version 
	 * every time a copy is needed. 
	 */
	public static class ImmutableWLCommand extends WLCommand{
		
		private static final long serialVersionUID = 7387553707370757534L;
		
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
		private ImmutableWLCommand(String triggerWord, Map<WLCommandComponent, Integer> commandsCountMap, 
				List<PosTerm> posList, int componentCount, int triggerWordIndex,
				int optionalTermsCount, Map<Integer, Integer> optionalTermsMap){
			
			super.triggerWord = triggerWord;
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
			String triggerWord;
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
			
			public Builder(String triggerWord, Map<WLCommandComponent, Integer> commandsCountMap, 
					ImmutableList<PosTerm> posList, int componentCount, int triggerWordIndex,
					int optionalTermsCount, ImmutableMap<Integer, Integer> optionalTermsMap){
				
				this.triggerWord = triggerWord;
				this.commandsCountMap = commandsCountMap;		
				this.posTermList = posList;
				this.totalComponentCount = componentCount;				
				this.triggerWordIndex = triggerWordIndex;
				this.optionalTermsCount = optionalTermsCount;
				this.optionalTermsGroupCountMap = optionalTermsMap;
			}
			
			public ImmutableWLCommand build(){
				return new ImmutableWLCommand(triggerWord, commandsCountMap, 
						posTermList, totalComponentCount, triggerWordIndex, 
						optionalTermsCount, optionalTermsGroupCountMap);				
			}			
		}		
	}	
	
	/**
	 * PosTerm stores a part of speech term, and the position in commandsMap
	 * it occurs, to build a WLCommand, ie turn triggered phrases into WL commands.
	 * 
	 */
	public static class PosTerm implements Serializable{		

		private static final long serialVersionUID = 5279307889713339412L;
		private static final int DEFAULT_ARG_NUM = -1;
		/**
		 * Enums used to indicate special connotations of PosTerm, 
		 * e.g. defining and defined terms 
		 */
		public static enum PosTermConnotation{
			DEFINING, /*e.g. entity field*/
			DEFINED, /*e.g. variable $F$*/
			NONE;			
		}
		
		/**
		 * Indicates special position the Struct filling the posTerm 
		 * needs to be in, e.g. first (left-most-child) 
		 */
		public static enum PositionInStructTree{
			FIRST,
			LAST, 
			ANY;
			public boolean isFirstTerm(){
				return this == FIRST;
			}
			public boolean isLastTerm(){
				return this == LAST;
			}
		}
		
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
		
		private PosTermConnotation posTermConnotation;
		private PositionInStructTree positionInStructTree;
		
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
		private boolean isPropertyTerm;
		/*the position for the argument in the arg list for command, e.g. "A ~HasProperty~ B"
		 * A has argNum 1 and B has argNum 2. This is automatically determined with respect to 
		 * the trigger term for binary operators, if argNum is not explicitly given.*/
		private int argNum = DEFAULT_ARG_NUM;
		private boolean isExprHead;
		private boolean isExprHeadArg;
		//relationType for building relation vectors. 
		private List<RelationType> relationType;
		
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
			//whether should be made into property term, if corresponding trigger allows so.
			//e.g. "this polynomial is not $1$", use "is" inside ppt term Math["$1"]"
			private boolean isPropertyTerm;
			private int argNum;
			private boolean isExprHead;
			private boolean isExprHeadArg;
			//0 by default
			private int optionalGroupNum;
			//relationType for building relation vectors. 
			List<RelationType> relationTypeList = new ArrayList<RelationType>();
			
			private PosTermConnotation posTermConnotation = PosTermConnotation.NONE;
			private PositionInStructTree positionInStructTree = PositionInStructTree.ANY;
			
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
			
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString,
					RelationType relationType){
				this(posStr, nameStr, includeInBuiltString);
				this.relationTypeList.add(relationType);
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
			
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString, boolean isTrigger,
					boolean isTriggerMathObj, RelationType relationType){
				this(posStr, nameStr, includeInBuiltString, isTrigger, isTriggerMathObj);
				this.relationTypeList.add(relationType);
			}
			
			/**
			 * @param posStr Part of speech string
			 * @param nameStr
			 * @param includeInBuiltString
			 * @param isTrigger
			 * @param isTriggerMathObj
			 */
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString, boolean isTrigger,
					boolean isTriggerMathObj, PosTermConnotation posTermConnotation){
				this(posStr, nameStr, includeInBuiltString);
				this.isTrigger = isTrigger;
				this.isTriggerMathObj = isTriggerMathObj;
				this.posTermConnotation = posTermConnotation;
			}
			
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString, boolean isTrigger,
					boolean isTriggerMathObj, PosTermConnotation posTermConnotation, RelationType relationType){
				this(posStr, nameStr, includeInBuiltString);
				this.isTrigger = isTrigger;
				this.isTriggerMathObj = isTriggerMathObj;
				this.posTermConnotation = posTermConnotation;
				this.relationTypeList.add(relationType);
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
			
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString,
					boolean isTriggerMathObj, String optionGroup, RelationType relationType){
				this(posStr, nameStr, includeInBuiltString, isTriggerMathObj, optionGroup);
				this.relationTypeList.add(relationType);
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
					boolean isTriggerMathObj, int positionInMap, PosTermConnotation posTermConnotation){
				
				this(posStr, nameStr, includeInBuiltString, isTrigger, isTriggerMathObj);
				this.positionInMap = positionInMap;
				this.posTermConnotation = posTermConnotation;
			}
	
			public PBuilder(String posStr, String nameStr, boolean includeInBuiltString,
					boolean isTriggerMathObj, String optionGroup, int positionInMap){
				
				this(posStr, nameStr, includeInBuiltString, isTriggerMathObj, optionGroup);
				this.positionInMap = positionInMap;
			}
			
			/**
			 * Auxiliary Strings, e.g. brackets "["., ~HasProperty~
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
			 * Adds relationType to current PBuilder. This obliviates the need for 
			 * combinatorial number of constructors.
			 * Does not compromise any later immutability, since still in Builder.
			 * @param relationType
			 * @return
			 */
			public PBuilder addRelationType(RelationType relationType){
				this.relationTypeList.add(relationType);
				return this;
			}
			
			public PBuilder addRelationType(RelationType[] relationTypeAr){
				this.relationTypeList.addAll(Arrays.asList(relationTypeAr));
				return this;
			}

			public PBuilder makePropertyTerm(){
				this.isPropertyTerm = true;
				return this;
			}
			
			public PBuilder updateArgNum(int argNum_){
				this.argNum = argNum_;
				return this;
			}
			
			/**
			 * Head of Expr, e.g. HasProperty in 
			 * "A ~HasProperty~ B" i.e. HasProperty[A, B]
			 * @return
			 */
			public PBuilder makeExprHead(){
				this.isExprHead = true;
				return this;
			}
			/**
			 * make into argument for exprHead, e.g. 
			 * "implies" in Connective["implies"].
			 * @return
			 */
			public PBuilder makeExprHeadArg(){
				this.isExprHeadArg = true;
				return this;
			}
			/**
			 * Set special position the struct for term must occur in Struct tree, e.g. FIRST.
			 * For e.g. "Fix a prime $p$"
			 * @param positionInStructTree_
			 * @return
			 */
			public PBuilder setPositionInStructTree(PositionInStructTree positionInStructTree_){
				this.positionInStructTree = positionInStructTree_;
				return this;
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
							isTriggerMathObj, optionalGroupNum, relationTypeList, positionInStructTree);
				}else if(this.isNegativeTerm){
					return new NegativePosTerm(commandComponent, positionInMap);
				}else{
					return new PosTerm(commandComponent, positionInMap, includeInBuiltString,
							isTrigger, isTriggerMathObj, posTermConnotation, relationTypeList, positionInStructTree,
							isPropertyTerm, argNum, isExprHead, isExprHeadArg);
				}
			}					
		}
		
		private PosTerm(WLCommandComponent commandComponent, int position, boolean includeInBuiltString,
				boolean isTrigger, boolean isTriggerMathObj, PosTermConnotation posTermConnotation,
				List<RelationType> relationTypeList, PositionInStructTree positionInStructTree, boolean isPropertyTerm_){
			this.commandComponent = commandComponent;
			this.positionInMap = position;
			this.includeInBuiltString = includeInBuiltString;
			this.triggerMathObj = isTriggerMathObj;
			this.isTrigger = isTrigger;
			this.posTermConnotation = posTermConnotation;
			this.relationType = relationTypeList;
			this.positionInStructTree = positionInStructTree;
			this.isPropertyTerm = isPropertyTerm_;
		}
		
		private PosTerm(WLCommandComponent commandComponent, int position, boolean includeInBuiltString,
				boolean isTrigger, boolean isTriggerMathObj, PosTermConnotation posTermConnotation,
				List<RelationType> relationTypeList, PositionInStructTree positionInStructTree, boolean isPropertyTerm_,
				int argNum_, boolean isExprHead_, boolean isExprHeadArg_){
			this(commandComponent, position, includeInBuiltString, isTrigger, isTriggerMathObj, posTermConnotation,
					relationTypeList, positionInStructTree, isPropertyTerm_);
			this.isExprHead = isExprHead_;
			this.isExprHeadArg = isExprHeadArg_;
			this.argNum = argNum_;
		}
		/**
		 * Creates deep copy of this PosTerm.
		 * @return
		 */
		public PosTerm termDeepCopy(){				
			return new PosTerm(commandComponent, positionInMap, includeInBuiltString,
					isTrigger, triggerMathObj, posTermConnotation, relationType, positionInStructTree,
					isPropertyTerm, argNum, isExprHead, isExprHeadArg);			
		}	
		
		/**
		 * @return the isOptionalTerm
		 */
		public boolean isTrigger() {
			return this.isTrigger;
		}
		/**
		 * Whether should be made into property term, if corresponding trigger allows so.
		 * e.g. "this polynomial is not $1$", use "is" inside ppt term Math["$1"]"
		 * @return
		 */
		public boolean isPropertyTerm(){
			return this.isPropertyTerm;
		}
		/**
		 * the position for the argument in the arg list for command, e.g. "A ~HasProperty~ B".
		 * A has argNum 1 and B has argNum 2. This is automatically determined with respect to 
		 * the trigger term for binary operators, if argNum is not explicitly given.
		 */
		public int argNum(){
			return this.argNum;
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
		/**
		 * @return whether the captured Struct must be first (DFS) Struct
		 * in Struct tree, i.e. left-most child.
		 */
		public boolean isFirstStructTerm(){
			return positionInStructTree.isFirstTerm();
		}
		/**
		 * @return whether the captured Struct must be last (DFS) Struct
		 * in Struct tree, i.e. right-most child.
		 */
		public boolean isLastStructTerm(){
			return positionInStructTree.isLastTerm();
		}
		
		public List<RelationType> relationType(){
			return this.relationType;
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder(30);
			sb.append("{").append(this.commandComponent).append(", ").append(this.positionInMap);
			if(null != this.posTermStruct){
				sb.append(", ").append(this.posTermStruct);
			}
			if(this.isNegativeTerm()){
				sb.append(", NEGATIVE");
			}
			sb.append("}");
			return sb.toString();
		}
		
		public WLCommandComponent commandComponent(){
			return this.commandComponent;
		}
		
		/**
		 * optionalGroupNum can only be called on optionalPosTerm.
		 * @return
		 */
		public int optionalGroupNum(){			
			throw new UnsupportedOperationException(
					"Cannot call optionalGroupNum() on a non-optional PosTerm!");
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
			//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
			//System.out.println("posTerm: " + this + " ||posTermStruct " + posTermStruct);
			
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
		
		public PosTermConnotation posTermConnotation(){
			return this.posTermConnotation;
		}
	}
	
	public static class NegativePosTerm extends PosTerm{
		
		private static final long serialVersionUID = -5938361941318473351L;

		public NegativePosTerm(WLCommandComponent commandComponent, int position){
			super(commandComponent, position, false, false, false, PosTermConnotation.NONE,
					new ArrayList<RelationType>(), PositionInStructTree.ANY, false);			
		}
		
		/**
		 * Creates deep copy of this NegativePosTerm.
		 * @return
		 */
		@Override
		public PosTerm termDeepCopy(){			
			return new NegativePosTerm(super.commandComponent, super.positionInMap);			
		}
		
		@Override
		public boolean isNegativeTerm(){
			return true;
		}		
	}
	
	public static class OptionalPosTerm extends PosTerm{

		private static final long serialVersionUID = 3334001130849221307L;

		/**
		 * The group number for this optional term.
		 */
		private int optionalGroupNum;
		
		public OptionalPosTerm(WLCommandComponent commandComponent, int position, boolean includeInBuiltString,
				boolean triggerMathObj, int optionalGroupNum, List<RelationType> relationType, 
				PositionInStructTree positionInStructTree) {
			//cannot be trigger if optional term
			super(commandComponent, position, includeInBuiltString, false, triggerMathObj,
					PosTermConnotation.NONE, relationType, positionInStructTree, false);
			this.optionalGroupNum = optionalGroupNum;			
		}
		
		/**
		 * Copies PosTerm.
		 * @return
		 */
		@Override
		public PosTerm termDeepCopy(){			
			return new OptionalPosTerm(super.commandComponent, super.positionInMap, super.includeInBuiltString,
					super.triggerMathObj, optionalGroupNum, super.relationType, super.positionInStructTree);			
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
		
		/*Trivial cast so the compiler can see the private fields of the superclass (WLCommand).
		  This trivial cast does not cost anything.*/
		newCommand.triggerWord = ((WLCommand)curCommand).triggerWord;
		newCommand.commandsMap = ArrayListMultimap.create(((WLCommand)curCommand).commandsMap);
		
		//commandsCountMap deliberately immutable, should not be modified during runtime
		newCommand.commandsCountMap = new HashMap<WLCommandComponent, Integer>(((WLCommand)curCommand).commandsCountMap) ;
		
		newCommand.composedWLCommandsList = new ArrayList<WLCommand>();
		
		newCommand.posTermList = new ArrayList<PosTerm>();		
		for(PosTerm term : ((WLCommand)curCommand).posTermList){
			newCommand.posTermList.add(term.termDeepCopy());
		}
		
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
	/**
	 * Shallow copy.
	 * Now should only copy for commands with optional terms!
	 * @param curCommand
	 * @return
	 */
	public static WLCommand shallowWLCommandCopy(WLCommand curCommand){	
		
		WLCommand newCommand = new WLCommand();
		
		curCommand.copyWithOptTermsCommand = newCommand;
		newCommand.copyWithOutOptTermsCommand = curCommand;
		newCommand.triggerWord = ((WLCommand)curCommand).triggerWord;
		newCommand.commandsMap = curCommand.commandsMap; //ArrayListMultimap.create(((WLCommand)curCommand).commandsMap);
		
		//commandsCountMap deliberately immutable, should not be modified during runtime
		newCommand.commandsCountMap = curCommand.commandsCountMap; //new HashMap<WLCommandComponent, Integer>(((WLCommand)curCommand).commandsCountMap) ;
		
		newCommand.composedWLCommandsList = curCommand.composedWLCommandsList; //new ArrayList<WLCommand>();
		
		newCommand.posTermList = curCommand.posTermList; /*new ArrayList<PosTerm>();		
		for(PosTerm term : ((WLCommand)curCommand).posTermList){
			newCommand.posTermList.add(term.termDeepCopy());
		}*/
		
		newCommand.totalComponentCount = ((WLCommand)curCommand).totalComponentCount;
		/*componentCounter should be 0, if copying to use for optional terms. */
		newCommand.componentCounter = curCommand.componentCounter; //((WLCommand)curCommand).totalComponentCount;

		newCommand.structsWithOtherHeadCount = curCommand.structsWithOtherHeadCount;
		newCommand.structHeadWithOtherHead = curCommand.structHeadWithOtherHead;
		newCommand.lastAddedCompIndex = curCommand.lastAddedCompIndex; //((WLCommand) curCommand).triggerWordIndex;
		newCommand.triggerWordIndex = ((WLCommand) curCommand).triggerWordIndex;

		newCommand.optionalTermsCount = curCommand.optionalTermsCount; //((WLCommand) curCommand).optionalTermsCount;
		newCommand.defaultOptionalTermsCount = curCommand.defaultOptionalTermsCount; //((WLCommand)curCommand).optionalTermsCount;
		
		newCommand.optionalTermsGroupCountMap = curCommand.optionalTermsGroupCountMap;
		/*if(null != ((WLCommand)curCommand).optionalTermsGroupCountMap){
			newCommand.optionalTermsGroupCountMap 
				= new HashMap<Integer, Integer>(((WLCommand)curCommand).optionalTermsGroupCountMap);			
		}*/
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
	 * Find struct with least depth (closest to root) amongst Structs that build this WLCommand,
	 * including the closest/deepest ancestor that connects any two structs used.
	 */
	private static Struct findCommandHead(ListMultimap<WLCommandComponent, Struct> commandsMap, WLCommand curCommand){
		Struct structToAppendCommandStr;
		/*if(commandsMap.size()==3){
			System.out.print("");
			//throw new IllegalStateException(commandsMap.toString());
		}*/
		//map to store Structs' parents. The integer can be left child (-1)
		//right child (1), or 0 (both children covered)
		Map<Struct, Integer> structIntMap = new HashMap<Struct, Integer>();
		Set<String> originalStructsNameStrSet = new HashSet<String>();
		int leastDepth = MAXDFSDEPTH;
		Struct highestStruct = null;
		
		for(Struct nextStruct : commandsMap.values()){
			originalStructsNameStrSet.add(nextStruct.nameStr());
		}
		//System.out.println("+++++++++++++++++++++commandsMap " + commandsMap);
		for(Struct nextStruct : commandsMap.values()){
			//should never be null, commandsMap should be all filled, except maybe optional posTerms.
			if(nextStruct != null){
				Struct nextStructParent = nextStruct.parentStruct();
				Struct curStruct = nextStruct;
				//System.out.println("+++nextStructParent " + nextStructParent);
				while(nextStructParent != null){					
					//System.out.println("***ParentPrev2" + nextStructParent.prev1() + " "  + nextStruct == nextStructParent.prev2());
					int whichChild = curStruct == nextStructParent.prev1() ? LEFTCHILD : 
						(curStruct == nextStructParent.prev2() ? RIGHTCHILD : NEITHERCHILD);
					
					/*To count in the "let" in commandNumUnits for e.g. "Let $p_r$ be the preimages under $w$" */
					if(whichChild == RIGHTCHILD && nextStructParent.prev1NodeType().equals(NodeType.STRUCTA)){
						String prev1StructNameStr = ((Struct)nextStructParent.prev1()).nameStr();
						if(!originalStructsNameStrSet.contains(prev1StructNameStr)){
							ParseStructType nameType = ParseStructType.getTypeFromName(prev1StructNameStr);	
							if(nameType == ParseStructType.HYP 
									|| nameType == ParseStructType.HYP_iff){
								increment_commandNumUnits(curCommand, 1);
							}
						}
					}
					
					if(structIntMap.containsKey(nextStructParent)){
						int existingChild = structIntMap.get(nextStructParent);
						if(nextStructParent.isStructA() && existingChild != NEITHERCHILD
								&& whichChild != existingChild){
							/*check if has left child, right child, or both.*/							
							structIntMap.put(nextStructParent, BOTHCHILDREN);
							//update curStruct so to correctly determine which child parent is 
							curStruct = nextStructParent;
							//colored twice, need to put its parent in map
							nextStructParent = nextStructParent.parentStruct();							
						}else{
							/*if(!nextStructParent.isStructA()){
								structIntMap.put(nextStructParent, existingChild+1);
							}*/
							break;
						}
					}else{
						//if(nextStructParent.isStructA()){
							structIntMap.put(nextStructParent, whichChild);
						//}
							/*else{
							//indicate one child for structH.
							structIntMap.put(nextStructParent, 1);
						}*/
						curStruct = nextStructParent;
						nextStructParent = nextStructParent.parentStruct(); 
					}					
				}				
			}			
		}
		//System.out.println("WLCommand - originalStructsNameStrSet " + originalStructsNameStrSet);
		for(Entry<Struct, Integer> entry : structIntMap.entrySet()){
			Integer whichChild = entry.getValue();
			Struct nextStruct = entry.getKey();
			//System.out.println("@@@Added Parent: " + nextStruct + " " + whichChild);
			
			if(!nextStruct.isStructA() 
					//Don't add StructH, even if all children present, unless original StructH present.
					&& originalStructsNameStrSet.contains(nextStruct.nameStr())
					//children parts could be added redundantly
					//&& whichChild >= nextStruct.children().size() 
					|| nextStruct.isStructA() && whichChild == BOTHCHILDREN){
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
		//if head is ent (firstPosTermStruct.type().equals("ent") ) or texAssert and 
		//everything in this command belongs to or is a child of the head ent struct				
		if(highestStruct.type().equals("texAssert") && null != highestStruct.parentStruct()){
			Struct parentStruct = highestStruct.parentStruct();
			String parentStructType = parentStruct.type();
			if("If".equals(parentStructType) || "hypo".equals(parentStructType) ){	
				boolean updateParentBool = true;
				if(parentStruct.prev1NodeType().isTypeStruct()){
					Struct siblingStruct = (Struct)parentStruct.prev1();
					Object s = siblingStruct.prev1();
					if(null != s){
						String siblingStructPrev1Str = s.toString();
						if(siblingStructPrev1Str.equals("for all") || siblingStructPrev1Str.equals("for every")
								|| siblingStructPrev1Str.equals("for any")){
							updateParentBool = false;
							//highestStruct = parentStruct;						
						}
					}
				}
				if(updateParentBool){
					highestStruct = parentStruct;
				}
			}
		}	
		structToAppendCommandStr = highestStruct;		
		//System.out.println("structToAppendCommandStr" + structToAppendCommandStr);
		return structToAppendCommandStr;
	}
	
	/**
	 * Update the wrapper list to update nextStruct's headStruct to structToAppendCommandStr.
	 * Used for compositing commands, so not to use same struct in multiple commands.
	 * Called during command *build* time.
	 * @param nextStruct
	 * @param structToAppendCommandStr
	 * @return Whether nextStruct already has associated head.
	 */
	private static boolean updateHeadStruct(Struct nextStruct, Struct structToAppendCommandStr, WLCommand curCommand){
		
		Struct prevHeadStruct = nextStruct.structToAppendCommandStr();		
		//whether the struct already has a head.
		boolean prevStructHeaded = false; 
		/*if(true || nextStruct.type().equals("assert") //&& ((Struct)nextStruct.prev1()).type().equals("det")
				){
			System.out.println("nextStruc " + nextStruct);
			System.out.println("structToAppendCommandStr: " + structToAppendCommandStr + " structToAppendCommandStr.dfsDepth() " + structToAppendCommandStr.dfsDepth());
			if(null != prevHeadStruct){
				System.out.println("prevHeadStruct " + prevHeadStruct+ " prevHeadStruct.dfsDepth() " + prevHeadStruct.dfsDepth());
			}
		}*/
		
		if (prevHeadStruct != null) {
			/*System.out.println("prevHeadStruct.dfsDepth() " + prevHeadStruct.dfsDepth() + " " + prevHeadStruct
					+ " nextStruct.dfsDepth() " + nextStruct.dfsDepth() + " " + nextStruct
					+ " structToAppendCommandStr.dfsDepth() " + structToAppendCommandStr.dfsDepth() + " " + structToAppendCommandStr);*/
			List<WLCommandWrapper> prevHeadStructWrapperList = prevHeadStruct.WLCommandWrapperList();
			//no need to update structsWithOtherHeadCount if the heads are already same. Note the two
			//commands could be different, just with the same head.
			//System.out.println("++++======+++++structToAppendCommandStr != prevHeadStruct "+ structToAppendCommandStr.dfsDepth() +" ++++" + prevHeadStruct.dfsDepth());
			int structToAppendCommandStrDfsDepth = structToAppendCommandStr.dfsDepth();
			int prevHeadStructDfsDepth = prevHeadStruct.dfsDepth();
			if(//structToAppendCommandStr != prevHeadStruct 
					/*Smaller depth is closer to root, so has wider span over the tree. */
					structToAppendCommandStrDfsDepth <= prevHeadStructDfsDepth){
				// in this case structToAppendCommandStr should not be
				// null either				
				//int wrapperListSz = prevHeadStructWrapperList.size();	
				//boolean leavePrevHeadInPlace = false;
				//get the last-added command. <--should iterate and add count to all previous commands
				//with this wrapper? <--command building goes inside-out
				/*Update all wrapper in prevHeadStructWrapperList since commands in all wrappers touch this struct*/
				if(prevHeadStructWrapperList != null){
					for(WLCommandWrapper wrapper : prevHeadStructWrapperList){
						//System.out.println("-------------prevHeadStructWrapperList wrapper: " + wrapper);
						WLCommand lastWrapperCommand = wrapper.WLCommand();				
						//System.out.println("curCommand: " + curCommand);
						// increment the headCount of the last wrapper object, should update every wrapper's count.
						if(!curCommand.equals(lastWrapperCommand)){
							lastWrapperCommand.structsWithOtherHeadCount++; //HERE
							if(prevHeadStruct.dfsDepth() == nextStruct.dfsDepth()){
								lastWrapperCommand.structHeadWithOtherHead = prevHeadStruct;								
							}
						}
					}
				}else if(structToAppendCommandStrDfsDepth == prevHeadStructDfsDepth){
					curCommand.structsWithOtherHeadCount++;
					if(structToAppendCommandStr.dfsDepth() == nextStruct.dfsDepth()){
						curCommand.structHeadWithOtherHead = structToAppendCommandStr;								
					}
				}
				nextStruct.set_structToAppendCommandStr(structToAppendCommandStr);
				structToAppendCommandStr.set_structToAppendCommandStr(structToAppendCommandStr);
				setAncestorStructToAppendCommandStr(nextStruct, structToAppendCommandStr, curCommand);
				
				//System.out.println("WLCommand - !!Case structToAppendCommandStr.dfsDepth() <= prevHeadStruct.dfsDepth()  ");
				//System.out.println("Wrapper command struct " + headStruct);
				//System.out.println("***Wrapper Command to update: " + lastWrapperCommand);				
			}else{
				curCommand.structsWithOtherHeadCount++;
				if(structToAppendCommandStr.dfsDepth() == nextStruct.dfsDepth()){
					curCommand.structHeadWithOtherHead = structToAppendCommandStr;								
				}
				//System.out.println("WLCOmmand ----- from updateWrapper()");
				//if(true) throw new RuntimeException("WLCommand " + curCommand);
			}			
			prevStructHeaded = true;			
		}else{
			nextStruct.set_structToAppendCommandStr(structToAppendCommandStr);
			structToAppendCommandStr.set_structToAppendCommandStr(structToAppendCommandStr);
			setAncestorStructToAppendCommandStr(nextStruct, structToAppendCommandStr, curCommand);
		}
		//structToAppendCommandStr.set_structToAppendCommandStr(structToAppendCommandStr);
		//nextStruct.set_structToAppendCommandStr(structToAppendCommandStr);
		return prevStructHeaded;
	}
	
	private static void setAncestorStructToAppendCommandStr(Struct nextStruct, Struct structToAppendCommandStr,
			WLCommand curCommand) {
		Struct parentStruct;
		Struct parentStructHead;
		/*if(!nextStruct.isStructA()){
			System.out.println("setAncestorHeadStruct nextStruct "+nextStruct);
			System.out.println("nextStruct.parent "+(parentStruct=nextStruct.parentStruct()));
			System.out.println("parentStruct.dfsDepth() > structToAppendCommandStr.dfsDepth()" + (parentStruct.structToAppendCommandStr().dfsDepth() > structToAppendCommandStr.dfsDepth()));
			System.out.println("parent structToAppendCommandStr " + parentStruct.structToAppendCommandStr() 
			+ " depth "+ parentStruct.structToAppendCommandStr().dfsDepth());
			System.out.println("structToAppendCommandStr " +structToAppendCommandStr + " structToAppendCommandStr.dfsDepth() " + structToAppendCommandStr.dfsDepth());
			System.out.println("WLCommand -setAncestorStruct curCommand " + curCommand);			
		}*/
		int structToAppendCommandStrDfsDepth = structToAppendCommandStr.dfsDepth();
		while(null != (parentStruct = nextStruct.parentStruct()) 
				&& parentStruct.dfsDepth() > structToAppendCommandStrDfsDepth
				){
			if(null == (parentStructHead = parentStruct.structToAppendCommandStr()) || parentStructHead.dfsDepth() > structToAppendCommandStr.dfsDepth()){
				parentStruct.set_structToAppendCommandStr(structToAppendCommandStr);
			}
			//if(!nextStruct.isStructA()){
				//System.out.println("WLCommand- setAncestorStruct nextStruct " + nextStruct);
				//System.out.println("WLCommand -setAncestorStruct nextStruct.structToAppendCommandStr() " + nextStruct.structToAppendCommandStr());
			//}
			nextStruct = parentStruct;
		}		
	}

	/**
	 * Builds the WLCommand from commandsMap & posTermList after it's satisfied.
	 * Should be called after being satisfied. 
	 * @param curCommand command being built
	 * @param firstPosTermStruct Struct to append the built CommandStr to.
	 * Right now not using this struct value, just to set previousBuiltStruct to not be null.
	 * @return String form of the resulting WLCommand
	 */
	public static String build(WLCommand curCommand, ParseState parseState) throws IllegalWLCommandStateException{
		//command is satisfied only if componentCounter is 0
		if(curCommand.componentCounter > 0){ 
			String msg = "build() is called, but componentCounter is still > 0! Current command: ";
			throw new IllegalWLCommandStateException(msg + curCommand);
		}
		//System.out.println("WLCommand - triggerWord " + curCommand.triggerWord);
		ListMultimap<WLCommandComponent, Struct> commandsMap = curCommand.commandsMap;
		//value is 0 if that group is satisfied
		Map<Integer, Integer> optionalTermsGroupCountMap = curCommand.optionalTermsGroupCountMap;
		
		//counts should now be all 0
		Map<WLCommandComponent, Integer> commandsCountMap = curCommand.commandsCountMap;
		List<PosTerm> posTermList = curCommand.posTermList;
		int posTermListSz = posTermList.size();
		//use StringBuilder!
		//StringBuilder commandSB = new StringBuilder();
		List<String> commandStrList = new ArrayList<String>();
		int commandStrListTriggerIndex = -1;
		int commandStrListCounter = 0;
		//the latest Struct to be touched, for determining if an aux String should be displayed
		//boolean prevStructHeaded = false;	
		//Struct headStruct = curCommand.headStruct;
		//determine which head to attach this command to
		Struct structToAppendCommandStr = findCommandHead(commandsMap, curCommand);
		
		EnumMap<PosTermConnotation, Struct> connotationMap = null;
		
		boolean commandNegatedBool = false;	
		boolean triggerTermNegated = false;
		PosTerm triggerPosTerm = posTermList.get(curCommand.triggerWordIndex);
		Struct triggerPosTermStruct = triggerPosTerm.posTermStruct;
		if(triggerPosTermStruct != null){
			if(TRIGGER_WORD_NOT_PATTERN.matcher(triggerPosTermStruct.nameStr()).matches()){
				commandNegatedBool = true;
			}
		}
		//int posTermListSz = posTermList.size();
		//map to keep track of arguments of expr's for the built command. The key is the position
		//(slot number) of the arg in the arg list.
		Map<Integer, List<Expr>> exprArgsListTMap = new TreeMap<Integer, List<Expr>>();
		//symbol string for Expr head, must be nontrivial for each WLCommand, 
		//e.g. "HasProperty" in "A ~HasProperty~ B"
		String exprHeadSymbolStr = null;
		//argument list for ExprHead, as in "implies" in "A ~Connective["implies"]~ B"
		List<Expr> exprHeadArgList = new ArrayList<Expr>();
		//boolean beforeExprHead = true;
		int curArgNumCount = 0;
		for(int i = 0; i < posTermListSz; i++){
			PosTerm term = posTermList.get(i);
			if(term.isNegativeTerm()){
				continue;
			}
			if(term.isExprHead){
				//beforeExprHead = false;
				//posIndex should be WLCommandsList.AUXINDEX or WLCommandsList.WL_DIRECTIVE_INDEX
				exprHeadSymbolStr = term.commandComponent.posStr;				
				curArgNumCount = 1;
			}
			if(!term.includeInBuiltString){	
				//set its head Struct to structToAppendCommandStr,
				// ***This should always be null, because haven't set it yet.
				Struct nextStruct = term.posTermStruct;				
				//System.out.println("&&&posTermStruct " + nextStruct);
				//get WLCommandWrapperList
				if(nextStruct != null){
					updateHeadStruct(nextStruct, structToAppendCommandStr, curCommand);
					
					/*Update commandNumUnits, which for terms that are included in built string would be updated 
					  via simpleToString() */
					increment_commandNumUnits(curCommand, nextStruct);
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
				}
				continue;
			}
			WLCommandComponent commandComponent = term.commandComponent;
			List<Expr> termExprList = new ArrayList<Expr>();
			int positionInMap = term.positionInMap;			
			String nextWord = "";
			//neither directive or auxilliary String, e.g. "~HasProperty~", "{"
			if(positionInMap != WLCommandsList.AUXINDEX && positionInMap != WLCommandsList.WL_DIRECTIVE_INDEX){
				
				List<Struct> curCommandComponentList = commandsMap.get(commandComponent);
				if(positionInMap >= curCommandComponentList.size()){
					if(DEBUG && !term.isOptionalTerm()){
						String warningMsg = "positionInMap: " + positionInMap +" list size: "
								+curCommandComponentList.size() +" Should not happen! For component: "
								+ commandComponent + " in command " + commandsCountMap;
						System.out.println(warningMsg);
						logger.error(warningMsg);
					}
					continue;
				}				
				//this is same as term.getPosTermStruct()
				Struct nextStruct = curCommandComponentList.get(positionInMap);
				
				if(nextStruct.previousBuiltStruct() != null){ 
					//set to null for next parse dfs iteration
					//****don't need to set to null here, but 
					// set to null altogether after entire dfs iteration
					nextStruct.set_previousBuiltStruct(null);						
					//continue;	
				}
				
				//connotation such as .DEFINING, .DEFINED, etc.
				PosTermConnotation curConnotation = term.posTermConnotation();
				if(PosTermConnotation.NONE != curConnotation){
					if(null == connotationMap){
						connotationMap = new EnumMap<PosTermConnotation, Struct>(PosTermConnotation.class);
					}
					connotationMap.put(curConnotation, nextStruct);
				}
				
				//check if need to trigger triggerMathObj
				/*if(term.triggerMathObj){
					//should check first if contains WLCommandStr, i.e. has been converted to some 
					//commands already
					//nextWord = TriggerMathObj3.get_mathObjFromStruct(nextStruct, curCommand);
					
					//if(nextWord.equals("")){
						//already added numUnits to Struct above, don't do it again.
						//nextWord = nextStruct.simpleToString(true, null);	
					//}
					nextWord = nextStruct.simpleToString(true, curCommand);
				}else{
					//takes into account pro, and the ent it should refer to
					nextWord = nextStruct.simpleToString(true, curCommand);
				}*/
				//	System.out.println("WLCommand triggerPosTer: " + triggerPosTerm.isPropertyTerm + " " + triggerPosTerm);
				nextWord = nextStruct.simpleToString(true, curCommand, triggerPosTerm, term, termExprList);
				System.out.println("WLCommand - term/termExprList " + term + " / " + termExprList);
				//System.out.println("WLCommand - nextWord: " + nextWord + " for struct: " + nextStruct);
				//simple way to present the Struct
				//set to the head struct the currently built command will be appended to
				nextStruct.set_previousBuiltStruct(structToAppendCommandStr);
				structToAppendCommandStr.set_posteriorBuiltStruct(nextStruct);				
				//check if been assigned to a different head
				//prevStructHeaded = updateWrapper(nextStruct, structToAppendCommandStr);
				updateHeadStruct(nextStruct, structToAppendCommandStr, curCommand);
				
				/*if(nextStruct.structToAppendCommandStr() == null){						
					prevStructHeaded = false;
				}else{
					//already been assigned to a different head
					nextStruct.structToAppendCommandStr().WLCommand().structsWithOtherHeadCount--;									
				}
				nextStruct.set_structToAppendCommandStr(structToAppendCommandStr); */				
			}
			else if(positionInMap == WLCommandsList.WL_DIRECTIVE_INDEX){
				//index indicating this is a WL directive
				//should change to use simpletoString from Struct
				nextWord = term.commandComponent.posStr;
				
				//in case of WLCommand eg \\[ELement]
				//this list should contain Structs that corresponds to a WLCommand
				List<Struct> curCommandComponentList = commandsMap.get(commandComponent);
				//set the previousBuiltStruct.
				//should have size > 0 always <--nope! if element is not a true WLCommand, like an auxilliary string
				if(curCommandComponentList.size() > 0){					
					Struct nextStruct = curCommandComponentList.get(0);
					updateHeadStruct(nextStruct, structToAppendCommandStr, curCommand);
										
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
				//auxilliary Strings inside a WLCommand, eg "[", "\[Element]", ~HasProperty~	
				nextWord = term.commandComponent.posStr;
				//System.out.print("nextWord : " + nextWord + "prevStruct: " + prevStructHeaded);
				if(commandNegatedBool){
					String negativeDirective = negativeTriggerCommandsMap.get(nextWord);
					if(null != negativeDirective){
						nextWord = negativeDirective;
						//update trigger word to remove the negative term, e.g. "does not"
						triggerTermNegated = true;
					}
					//System.out.println("WLCommand - negativeDirective " + negativeDirective + " nextWord: " + nextWord);					
				}
			}
			
			if(term.isOptionalTerm()){
				int optionalGroupNum = term.optionalGroupNum();				
				if(0 == optionalTermsGroupCountMap.get(optionalGroupNum)){
					//commandSB.append(nextWord);//.append(" ");
					commandStrList.add(nextWord);
					if(term.isTrigger){
						commandStrListTriggerIndex = commandStrListCounter;
					}
					commandStrListCounter++;
					addTermExprToTMap(exprArgsListTMap, curArgNumCount, term, termExprList);					
				}
			}else{
				//commandSB.append(nextWord);//.append(" ");
				commandStrList.add(nextWord);
				if(term.isTrigger){
					commandStrListTriggerIndex = commandStrListCounter;
				}
				commandStrListCounter++;
				//depends on whether the term is the an arg to the head Expr.
				if(term.isExprHeadArg){
					exprHeadArgList.addAll(termExprList);
				}else{					
					addTermExprToTMap(exprArgsListTMap, curArgNumCount, term, termExprList);									
				}
			}
		}		
		if(null == exprHeadSymbolStr){
			String msg = "exprHeadSymbolStr for command cannot be null!";
			logger.error(msg);
			throw new IllegalWLCommandStateException(msg);
		}
		//"****%###structToAppendCommandStr " +structToAppendCommandStr	
		WLCommandWrapper curCommandWrapper = structToAppendCommandStr.add_WLCommandWrapper(curCommand);
		
		//add local variable to parseState.
		if(null != connotationMap && connotationMap.containsKey(PosTermConnotation.DEFINED)
				&& connotationMap.containsKey(PosTermConnotation.DEFINING)){
			Struct definingStruct = connotationMap.get(PosTermConnotation.DEFINING);
			String variableName = connotationMap.get(PosTermConnotation.DEFINED).nameStr();
			if(!variableName.equals("")){
				parseState.addLocalVariableStructPair(variableName, definingStruct);
				//System.out.println("variableName: " + variableName);
			}
		}		
		//gather together the WLCommand Expr
		Expr headSymbolExpr;
		if(exprHeadArgList.isEmpty()){			
			headSymbolExpr = new Expr(Expr.SYMBOL, exprHeadSymbolStr);
		}else{
			headSymbolExpr = ExprUtils.createExprFromList(exprHeadSymbolStr, exprHeadArgList);
		}
		//consolidating the Expr's inside the args lists into a single Expr. So each argument
		//is represented by a single Expr.
		Expr[] exprArgsAr = new Expr[exprArgsListTMap.size()];
		int exprArgsArCounter = 0;
		for(Map.Entry<Integer, List<Expr>> entry : exprArgsListTMap.entrySet()){
			Expr entryExpr;
			List<Expr> singleArgList = entry.getValue();
			int singleArgListSz = singleArgList.size();
			if(singleArgListSz > 1){
				entryExpr = ExprUtils.listExpr(singleArgList);
			}else{
				entryExpr = singleArgList.get(0);
			}
			exprArgsAr[exprArgsArCounter++] = entryExpr;			
		}		
		Expr commandHeadExpr = new Expr(headSymbolExpr, exprArgsAr);
		
		//gather together the WLCommand Str
		StringBuilder commandSB2 = new StringBuilder(100);
		for(int i = 0; i < commandStrList.size(); i++){
			String strToAppend = commandStrList.get(i);
			if(triggerTermNegated && i == commandStrListTriggerIndex){
				strToAppend = NEGATIVE_TRIGGER_PATTERN.matcher(strToAppend).replaceAll("");
			}
			//System.out.println("triggerTermNegated  strToAppend: " + triggerTermNegated+ " .. " + strToAppend);
			commandSB2.append(strToAppend);
		}
		
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		if(DEBUG){
			System.out.println("\n CUR COMMAND: triggerWord " +curCommand.getTriggerWord() + ". " + curCommand + " ");
			System.out.print("BUILT COMMAND: " + commandSB2);
			System.out.println("HEAD STRUCT: " + structToAppendCommandStr + " structsWithOtherHeadCount: " + curCommand.structsWithOtherHeadCount);
			System.out.println("WLCommand - *******%######structsWithOtherHeadCount " +curCommand.structsWithOtherHeadCount +" " +curCommand );
		}
		
		curCommandWrapper.set_commandExpr(commandHeadExpr);
		curCommandWrapper.set_highestStruct(structToAppendCommandStr);		
		curCommandWrapper.set_WLCommandStr(commandSB2);
		return commandSB2.toString();
	}

	/**
	 * @param exprArgsListTMap
	 * @param curArgNumCount
	 * @param term
	 * @param termExprList
	 */
	private static void addTermExprToTMap(Map<Integer, List<Expr>> exprArgsListTMap, int curArgNumCount, PosTerm term,
			List<Expr> termExprList) {
		//insert into TreeMap with appropriate slot number, 
		int termSlotNum = term.argNum == PosTerm.DEFAULT_ARG_NUM ? curArgNumCount : term.argNum;
		List<Expr> argNumExprList = exprArgsListTMap.get(termSlotNum);
		if(null == argNumExprList){
			argNumExprList = new ArrayList<Expr>();
			argNumExprList.addAll(termExprList);
			exprArgsListTMap.put(termSlotNum, argNumExprList);
		}else{
			argNumExprList.addAll(termExprList);
		}
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
		private boolean onlyOptionalTermAdded;
		
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
		
		public void setOptionalTermsAdded(){
			this.onlyOptionalTermAdded = true;
		}
		
		public boolean onlyOptionalTermAdded(){
			return onlyOptionalTermAdded;
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
	 * Auxiliary to addComponent. Optional terms count as nontrivial, 
	 * because could have optional terms 
	 * @param posTermList
	 * @param curIndex
	 * @return
	 */
	private static boolean onlyTrivialTermsBefore(List<PosTerm> posTermList, int curIndex){
		for(int i = curIndex; i > -1; i--){
			PosTerm posTerm = posTermList.get(i);
			//return false if any nontrivial term is encountered, optional terms count as *nontrivial*.
			if(!posTerm.isNegativeTerm() && posTerm.positionInMap() > -1){
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Adds new Struct to commandsMap.
	 * @param curCommand	WLCommand we are adding PosTerm to
	 * @param newSrtuct 	Pointer to a Struct to be added to curCommand
	 * @param before Whether before triggerTerm
	 * @return 				Whether the command is now satisfied
	 * 
	 * Add to commandsMap only if component is required as indicated by commandsCountMap.
	 * BUT: what if the Struct just added isn't the one needed? Keep adding.
	 * If the name could be several optional ones, eg "in" or "of", so use regex .match("in|of")
	 */
	public static CommandSat addComponent(WLCommand curCommand, Struct newStruct, boolean before){
		
		/*Short circuit if command already satisfied, so newStruct is intended to be added for an optional term,
		 * but newStruct has already been used in a non-optional term in the same command. */
		if(curCommand.componentCounter < 1 && newStruct.usedInOtherCommandComponent(curCommand)){
			//if(true) throw new IllegalStateException();
			boolean hasOptionalTermsLeft = (curCommand.optionalTermsCount > 0);
			return new CommandSat(true, hasOptionalTermsLeft, false);
		}
		/*if(curCommand.triggerWord.equals("if")){
			System.out.println("WLCommand ~"+ curCommand);
		}*/		
		//Get appropriate type if could be conj_ or disj_.
		String structPreType = newStruct.type();
		String structType = CONJ_DISJ_PATTERN.matcher(structPreType).find() ?
				structPreType.split("_")[1] : structPreType;			
			
		Map<WLCommandComponent, Integer> commandsCountMap = curCommand.commandsCountMap;
		if(disqualifyCommand(structType, commandsCountMap)){
			boolean disqualified = true;
			//if(true)throw new IllegalStateException(structType + " " + commandsCountMap);
			//System.out.println("disqualifying command. ###curCommand.commandsCountMap " + curCommand.commandsCountMap);
			return new CommandSat(disqualified);
		}

		Multimap<WLCommandComponent, Struct> commandsMap = curCommand.commandsMap;
		
		String structName = !newStruct.isStructA() ? newStruct.struct().get("name") : 
			newStruct.prev1NodeType().equals(NodeType.STR) ? (String)newStruct.prev1() : "";
		//System.out.println("inside addComponent, newStruct: " + newStruct);
		//System.out.println("###curCommand.triggerWord " + curCommand.triggerWord);
		//System.out.println("###commandsMap commandsMap " + curCommand.commandsMap);
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
		//System.out.println("GOT HERE****** posTermList " + posTermList + " i-1: " + (lastAddedComponentIndex-1));
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
		//checked here!
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
		
		Pattern commandComponentPosPattern = commandComponent.getPosPattern();
		Pattern commandComponentNamePattern = commandComponent.getNamePattern();		
		
		while( /* if auxilliary term*/
				posTermPositionInMap < 0
				|| (!commandComponentPosPattern.matcher(structType).find() || !commandComponentNamePattern.matcher(structName).find())
				//!structType.matches(commandComponentPosTerm) || !structName.matches(commandComponentName)) 
				&& (isOptionalTerm || curPosTerm.isNegativeTerm())				
				/* ensure index within bounds*/
				//&& i < posTermListSz - 1 
				){
			
			if(before){				
				i--;
				if(i < 0){
					boolean hasOptionalTermsLeft = (curCommand.optionalTermsCount > 0);
					return new CommandSat(curCommand.componentCounter < 1, hasOptionalTermsLeft, componentAdded);
				}
			}else{
				i++;
				if(i > posTermListSz - 1){
					boolean hasOptionalTermsLeft = (curCommand.optionalTermsCount > 0);					
					return new CommandSat(curCommand.componentCounter < 1, hasOptionalTermsLeft, componentAdded);
				}				
			}
			
			curPosTerm = posTermList.get(i);
			
			commandComponent = curPosTerm.commandComponent;
			isOptionalTerm = curPosTerm.isOptionalTerm();
			posTermPositionInMap = curPosTerm.positionInMap;
			
			commandComponentPosPattern = commandComponent.posPattern;
			commandComponentNamePattern = commandComponent.namePattern;			
			//commandComponentPosTerm = commandComponent.posStr;
			//commandComponentName = commandComponent.nameStr;
		
			//System.out.println("commandComponentNamePattern: "+commandComponentPosPattern  + " curPosTerm " + curPosTerm );
			//System.out.println("commandComponentPosPattern.matcher(structType)" + commandComponentPosPattern.matcher(structType));
					//|| !commandComponentNamePattern.matcher(structName).find()));
			//System.out.println("commandComponentNamePattern: "+commandComponentNamePattern+ " structType: " + 
					//commandComponentPosPattern.matcher(structType) + (isOptionalTerm || curPosTerm.isNegativeTerm()) + posTermPositionInMap);
		}
		
		//System.out.println("GOT HERE****** newStruct " + newStruct);
		//int addedComponentsColSz = commandsMap.get(commandComponent).size();
		
		//disqualify term if negative term triggered
		if(curPosTerm.isNegativeTerm() 
				&& commandComponentPosPattern.matcher(structType).find()
				&& commandComponentNamePattern.matcher(structName).find()){
			//System.out.println("WLCommand- addComponent negative term?: "+ curPosTerm.isNegativeTerm() + " " + newStruct);			
			boolean disqualified = true;
			return new CommandSat(disqualified);
		}
		
		if(commandComponentPosPattern.matcher(structType).matches()
				//structType.matches(commandComponentPosTerm) 	
				&& commandComponentNamePattern.matcher(structName).matches()
				//&& structName.matches(commandComponentName)
				/* this component is actually needed */
				&& commandsCountMap.get(commandComponent) > 0
				//&& addedComponentsColSz < commandComponentCount
				){
			//System.out.println("#####inside addComponent, ADDING newStruct: " + newStruct);
			/*if(newStruct.nameStr().equals("there")){
				System.out.print("");
			}*/
			//check for parent, see if has same type & name etc, if going backwards.
			if(before){
				newStruct = findMatchingParent(commandComponentPosPattern, commandComponentNamePattern, newStruct, curCommand);
			}
			
			//check to see if indeed on either first or last branch
			if(checkIfFirstLastTermStructDisqualified(newStruct, curPosTerm)){
				boolean disqualified = true;
				return new CommandSat(disqualified);
			}
			
			commandsMap.put(commandComponent, newStruct);
			//posTermList.get(i).set...
			curPosTerm.set_posTermStruct(newStruct);
			//here newComponent must have been in the original required set
			int commandComponentCount = commandsCountMap.get(commandComponent);
			commandsCountMap.put(commandComponent, commandComponentCount - 1);
			
			newStruct.add_usedInCommand(curCommand);			
			//use counter to track whether map is satisfied
			if(!isOptionalTerm){
				curCommand.componentCounter--;
			}else{
				//System.out.println("*******************curCommand.optionalTermsCount " + curCommand.optionalTermsCount);
				curCommand.optionalTermsCount--;
				//System.out.println("*******************curCommand.optionalTermsCount " + curCommand.optionalTermsCount);
				int optionalGroupNum = curPosTerm.optionalGroupNum();
				//decrement optional terms
				//optionalTermsGroupCountMap cannot be null if grammar rules are valid.
				assert(null != optionalTermsGroupCountMap);
				
				Integer curCount = optionalTermsGroupCountMap.get(optionalGroupNum);
				if(null != curCount){
					optionalTermsGroupCountMap.put(optionalGroupNum, curCount-1);
				}
			}			
			curCommand.lastAddedCompIndex = i;
			componentAdded = true;			
			boolean hasOptionalTermsLeft = (curCommand.optionalTermsCount > 0);
			
			CommandSat commandSat = 
					new CommandSat(curCommand.componentCounter < 1, hasOptionalTermsLeft, componentAdded);
			if(isOptionalTerm){
				commandSat.setOptionalTermsAdded();
			}
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
	 * @param newStruct
	 * @param curPosTerm
	 * @return whether disqualified
	 */
	private static boolean checkIfFirstLastTermStructDisqualified(Struct newStruct, PosTerm curPosTerm) {
		if(curPosTerm.isFirstStructTerm() && !checkOnLeftBranch(newStruct)){
			return true;
		}
		if(curPosTerm.isLastStructTerm() && !checkOnRightBranch(newStruct)){
			return true;
		}
		return false;
	}

	/**
	 * Check if struct lies on left-most branch of structTree.
	 * I.e. always left child of parent.
	 * @param struct
	 * @return
	 */
	private static boolean checkOnLeftBranch(Struct struct) {
		//if(true) throw new IllegalStateException();
		Struct structParent = struct.parentStruct();		
		while(null != structParent){
			//child of structH can't be the first-occuring Struct in struct list 
			if(!structParent.isStructA() || struct != structParent.prev1()){
				return false;
			}
			struct = structParent;
			structParent = struct.parentStruct();
		}		
		return true;
	}
		
	/**
	 * Check if struct lies on left-most branch of structTree.
	 * I.e. always left child of parent.
	 * @param struct
	 * @return
	 */
	private static boolean checkOnRightBranch(Struct struct) {

		Struct structParent = struct.parentStruct();		
		while(null != structParent){
			if(structParent.isStructA() && struct != structParent.prev2()){
				return false;
			}
			struct = structParent;
			structParent = struct.parentStruct();
		}		
		return true;
	}

	/**
	 * Find parent/ancestors if they satisfy same type/name requirements, if going backwards
	 * through the parse tree in dfs traversal order. E.g. $A$ in $B$ is p. should go to the
	 * $A$ as the subject rather than stopping at $B$. Should be called only if struct being
	 * added occurs before the trigger term.
	 * @param componentPosPattern
	 * @param componentNamePattern
	 * @param struct
	 * @return
	 */
	private static Struct findMatchingParent(Pattern componentPosPattern, Pattern componentNamePattern,
			Struct struct, WLCommand curCommand){
		
		Struct structToAdd = struct;
		Struct structParent = struct.parentStruct();
		if(struct.nameStr().equals("there")){
			System.out.print("");
		}		
		while(structParent != null){
			String structParentType = structParent.type();
			String parentType = CONJ_DISJ_PATTERN.matcher(structParentType).matches() ?
					//curStructInDequeParent.type().matches("conj_.+|disj_.+") ?
					structParentType.split("_")[1] : structParentType;					
			String parentNameStr = "";			
			if(structParent.isStructA()){
				if(structParent.prev1NodeType().equals(NodeType.STR)){
					parentNameStr = structParent.prev1().toString();
				}
			}else{
				parentNameStr = structParent.struct().get("name");
			}
			/*should match both type and term. Get parent of struct, e.g. "log of f is g" should get all of
			 *log of f", instead of just "f". I.e. get all of StructH.*/			
			if(componentNamePattern.matcher(parentNameStr).find()								
					//parentNameStr.matches(curCommandComponent.nameStr()) 
					&& componentPosPattern.matcher(parentType).find()){
				/*if(!structToAdd.isStructA()){
					System.out.println("++++structToAdd " + structToAdd + " parent : " + structToAdd.parentStruct());
				}*/
				//structToAdd.add_usedInCommand(curCommand); <--removes children. e.g. the holonomy of $\\partial \\Sigma$ has no fixed points
				structToAdd = structParent;
				structParent = structParent.parentStruct();				
				
			}//super hacky, find a better way!! By setting the parent, couldn't set for some reason this time
			// <--Nov 2016. All ents such that ent's could skip two generations, i.e. be grandparent of ent.
			//commented out Nov 2016.
			/*else if( (structParent.type().equals("prep") || structParent.type().equals("phrase"))
					//&& !structParent.childRelationType().equals(ChildRelationType.OTHER) 
					&& componentPosPattern.matcher("ent").find()
					){
				Struct parent = structParent.parentStruct();
				boolean added = false;
				
				if(parent != null ){
					Struct grandParent = structParent.parentStruct();
					if(grandParent != null && !grandParent.isStructA()
							|| !parent.isStructA()){
						//structToAdd = structParent;
						//structParent = structParent.parentStruct();
						//added = true;
					}
				}
				
				if(!added){
					break;
				}
			}
			else if(!structParent.isStructA() && !structParent.childRelationType().equals(ChildRelationType.OTHER)){
				structToAdd = structParent;
				structParent = structParent.parentStruct();

			}*/
			else{				
				break;
			}
		}
		//e.g. "$p$ such that $p$ is a prime is odd or even"
		Struct structGrandParent;
		Struct newBaseStruct = null;
		if(null != structParent && componentPosPattern.matcher("ent").matches() 
				&& (structParent.childRelationType().equals(ChildRelationType.HYP)
				|| (structGrandParent=structParent.parentStruct()) != null
					&& structGrandParent.childRelationType().equals(ChildRelationType.HYP)
					&& (newBaseStruct = structGrandParent) != null//trivial check to make assignment
				)
				){
			if(null == newBaseStruct){
				newBaseStruct = structParent;
			}
			Struct newParentStruct = newBaseStruct.parentStruct();
			if(null != newParentStruct && componentPosPattern.matcher(newParentStruct.type()).matches()){
				//structToAdd = newParentStruct;//HERE
				//newBaseStruct.add_usedInCommand(curCommand);
				//System.out.println("WLCommand - structToAdd.children " + structToAdd.children());
			}		
		}
		/*if(print){
			System.out.println("**************************** after: " + structToAdd);
		} */
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
		
		//if structPosStr is of a type that likely disqualifies a command, e.g. "if".
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
		if(disqualifyMatcher.matches()){			
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
				//System.out.println(Pattern.compile("verb$").matcher("verbphrase").find());
				if(posPattern.matcher("verb").find() && !isPosWildCard){
					return true;					
				}
			}
			if(disqualifyPos != null && posPattern.matcher(disqualifyPos).find() && !isPosWildCard /*not .* */){
				disqualifyPos = null;
			}
		}
		
		//disqualified 
		if(null != disqualifyPos){
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
	public static CommandSat addTriggerComponent(WLCommand curCommand, Struct newStruct){
		//System.out.println("Adding trigger component " + newStruct + " " + curCommand);
		
		PosTerm triggerPosTerm = curCommand.posTermList.get(curCommand.triggerWordIndex);
		//check to see if indeed on either first or last branch
		if(checkIfFirstLastTermStructDisqualified(newStruct, triggerPosTerm)){
			boolean disqualified = true;
			return new CommandSat(disqualified);
		}
		WLCommandComponent commandComponent = triggerPosTerm.commandComponent;	
		triggerPosTerm.set_posTermStruct(newStruct);
		newStruct.add_usedInCommand(curCommand);
		int commandComponentCount = curCommand.commandsCountMap.get(commandComponent);
		
		curCommand.commandsMap.put(commandComponent, newStruct);
		//here newComponent must have been in the original required set
		curCommand.commandsCountMap.put(commandComponent, commandComponentCount - 1);
		//use counter to track whether map is satisfied
		curCommand.componentCounter--;
		
		boolean hasOptionalTermsLeft = (curCommand.optionalTermsCount > 0);
		boolean componentAdded = true;
		return new CommandSat(curCommand.componentCounter < 1, hasOptionalTermsLeft, componentAdded);
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
	 * WLCommand curCommand copied to, 
	 * only applicable for WLCommands with optional terms.
	 * @param curCommand
	 * @return
	 */
	public WLCommand getCopyWithOptTermsCommand(){
		return this.copyWithOptTermsCommand;
	}
	
	/**
	 * WLCommand copied from, the copy without optional terms. 
	 * Only applicable for WLCommands with optional terms
	 * in its posTermList.
	 * @param curCommand
	 * @return
	 */
	public WLCommand getCopyWithOutOptTermsCommand(){
		return this.copyWithOutOptTermsCommand;
	}
	
	/**
	 * @param curCommand
	 * @return posTermList of current command
	 */
	public static List<PosTerm> posTermList(WLCommand curCommand){
		//System.out.println("curCommand " + curCommand + " " +Arrays.toString(Thread.currentThread().getStackTrace()));
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
	 * Counter to keep track of how many Structs, with which the current Struct this WLCommand
	 * instance is associated to (has this struct as structToAppendWLCommandStr), are associated with
	 * another head. I.e. # of structs that are used in multiple commands. This is used to avoid
	 * doubly using commands when compositing them.
	 * If this number is above some threshold (e.g. > totalComponentCount-1), 
	 * do not use this WLCommand.
	 * @param curCommand
	 * @return
	 */
	public static int structsWithOtherHeadCount(WLCommand curCommand){
		return curCommand.structsWithOtherHeadCount;
	}
	
	/**Struct with other head, such that the struct is the head of the command itself. 
	 * This means we can use a built command when, even if 
	 * structsWithOtherHeadCount == 1, but the struct used is the head of the other command.
	 * E.g. If assertion, here assertion can be used in some other command, but the built assertion
	 * command can still be used in the bigger "if assert" command, because assertion is the head of
	 * the inner command built*/
	public Struct structHeadWithOtherHead(){
		return this.structHeadWithOtherHead;
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
	 * Increment the commandNumUnits by 1, only if newStruct is a leaf node, to avoid double counting
	 * per Struct. In particular it's not recursive.
	 * @param curCommand
	 */
	public static boolean increment_commandNumUnits(WLCommand curCommand, Struct newStruct){
		//don't add for insignificant tokens, that would only be counted when occuring as StructA
		//and not StructH.
		String newStructType = newStruct.type();
		if(newStructType.equals("pre")){
			return false;
		}
		if(!newStruct.isStructA() 
			|| newStruct.prev1NodeType().equals(NodeType.STR) && newStruct.prev2NodeType().equals(NodeType.STR)){
			if(newStruct.type().equals("let")) {//throw new IllegalStateException(curCommand.toString());
				System.out.print("");
			}
			curCommand.commandNumUnits++;	
			//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
			//System.out.println("WLCommand increment_commandNumUnits - newStruct " + newStruct);
			return true;
		}
		return false;
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
	 * Does *NOT* mean optional terms satisfied as well.
	 * 
	 */
	public static boolean isSatisfied(WLCommand curCommand){
		//shouldn't be < 0!
		return curCommand.componentCounter < 1;
	}
	
	public boolean isSatisfiedWithOptionalTerms(){
		//shouldn't be < 0!
		return this.componentCounter < 1 && this.optionalTermsCount < 1;
	}
	
	@Override
	public String toString(){
		return this.posTermList.toString();
	}
	
	/**
	 * 
	 */
	public static class WLCommandComponent implements Serializable{
		
		private static final long serialVersionUID = -2853392387847693092L;

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
				this.posPattern = Pattern.compile("^(?:" + posTerm + ")$");				
				this.namePattern = Pattern.compile(name);
				
			}else{
				//if auxiliary component, compile trivial patterns instead of 
				//leaving them as null, to avoid any potential NPE.
				this.posPattern = Pattern.compile("");
				this.namePattern = Pattern.compile("");
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
		 * equals for WLCommandComponent
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
	 */
	public enum PosTermType{
		//stop the command (untrigger) once encountered.
		NEGATIVE;
	}

	/**
	 * 
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + defaultOptionalTermsCount;
		result = prime * result + totalComponentCount;
		result = prime * result + ((triggerWord == null) ? 0 : triggerWord.hashCode());
		result = prime * result + triggerWordIndex;
		result = prime * result + posTermList.size();		
		return result;
	}

	/**
	 * For WLCommand.
	 * Shallow equals.
	 */
	@Override
	public boolean equals(Object obj) {
		//if (true) return false;
		if (this == obj){
			return true;
		}
		if (obj == null){
			return false;
		}
		if (!(obj instanceof WLCommand)){
			return false;
		}
		WLCommand other = (WLCommand) obj;
		if (defaultOptionalTermsCount != other.defaultOptionalTermsCount)
			return false;
		if (totalComponentCount != other.totalComponentCount)
			return false;
		if (triggerWord == null) {
			if (other.triggerWord != null)
				return false;
		} else if (!triggerWord.equals(other.triggerWord)){
			return false;
		}
		if (triggerWordIndex != other.triggerWordIndex){
			return false;
		}
		if(posTermList.size() != other.posTermList.size()){
			return false;
		}
		return true;
	}
	
}
