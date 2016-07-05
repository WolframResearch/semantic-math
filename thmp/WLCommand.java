package thmp;

import java.util.List;

import com.google.common.collect.ListMultimap;

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
	private Hashmap<WLCommandComponent, Integer> commandsCountMap;
	
	private String triggerWord;
	//which WL expression to turn into using map components and how.
	//need to keep references to Structs in commandsMap
	// List of PosTerm with its position, {entsymb, 0}, {\[Element], -1}, {entsymb,2}
	//entsymb, 0, entsymb 1, use these to build grammar
	private List<PosTerm> posTermList;

	/**
	 * Whether this WLCommand has all the posTerms it needs yet.
	 */
	private boolean isSatisfied;
	
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
		private String posTerm;
		
		/**
		 * position of the relevant term inside a list in commandsMap.
		 * Ie the order it shows up in in the built-out command.
		 * -1 if it's a WL command, eg \[Element].
		 */
		private int positionInCommand;
		
		public PosTerm(String posTerm, int position){
			this.posTerm = posTerm;
			this.positionInCommand = position;
		}
		
		public String posTerm(){
			return this.posTerm;
		}
		
		public int positionInCommand(){
			return this.positionInCommand;
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
	 */
	public static WLCommand create(ListMultimap<WLCommandComponent, Integer> commandsCount){
		//defensively copy?? Even though not external-facing
		WLCommand curCommand = new WLCommand();
		
		curCommand.commandsCountMap = commandsCount;
		
		return curCommand;
	}
	
	/**
	 * Builds the WLCommand from commandsMap & posTermList after it's satisfied.
	 * Should be called after being satisfied. 
	 * @return String form of the resulting WLCommand
	 */
	public static String build(){
		
	}
	
	/**
	 * 
	 * @return whether the command is now satisfied
	 */
	public static boolean addPosTerm(PosTerm newTerm){
		posTermMap
	}

	/**
	 * Is this command (commandsMap) satisfied. 
	 * @return
	 */
	public static boolean isSatisfied(){
		
	}
	
	/**
	 * 
	 *
	 */
	public static class WLCommandComponent{
		//types should be consistent with types in Map
		//eg ent, symb, pre, etc
		private String posTerm;
		//eg "of". name could be wildcard *
		private String name;
		
		
		public WLCommandComponent(String posTerm, String name){
			this.posTerm = posTerm;
			this.name = name;
		}
		
		public String posTerm(){
			return this.posTerm;
		}
		
		@Override
		public boolean equals(Object obj){
			
		}
		
		@Override
		public int hashCode(){
			//this does not produce uniform distribution! Need to do some shifting
			int hashcode = this.posTerm.hashCode();
			hashcode += this.name.hashCode();
			return hashcode;
		}
	}
	
}
