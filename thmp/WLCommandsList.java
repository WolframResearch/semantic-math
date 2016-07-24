package thmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import thmp.WLCommand.PosTerm;
import thmp.WLCommand.WLCommandComponent;

/**
 * list of WLCommand's. Creates WLCommands using data here and stores them in an
 * immutable Multimap, keys being trigger words.
 *
 */
public class WLCommandsList {

	/**
	 * Map, keys are trigger words, values are pointers to WLCommand's
	 */
	private static final ImmutableMultimap<String, WLCommand> WLCommandMap; 
	
	/**
	 * ImmutableMultimap of which strings lead to which trigger words. It's a
	 * many-to-many mapping: one string can lead to many, and many can lead to
	 * one, trigger word. Strings that don't have entries should subsequently
	 * look up in WLCommandMap directly.
	 */
	private static final ImmutableMultimap<String, String> triggerWordLookupMap;

	/**
	 * Final constants used to indicate WLCommand/Aux String as posIndex in PosList.
	 */
	public static final int WLCOMMANDINDEX = -1;
	public static final int AUXINDEX = -2;
	private static final String TRIGGERMATHOBJSTR = "TriggerMathObj";
	
	/**
	 * 
	 */
	static {
		ImmutableListMultimap.Builder<String, String> triggerWordLookupMapBuilder = ImmutableListMultimap.builder();
		//need to put all vbs in here programmatically
		triggerWordLookupMapBuilder.put("be", "is");
		triggerWordLookupMapBuilder.put("are", "is");
		triggerWordLookupMapBuilder.put("has", "is");
		// triggerWordLookupMapBuilder.put("radius", "is");

		triggerWordLookupMap = triggerWordLookupMapBuilder.build();

		// builder for WLComamndMap,
		ImmutableMultimap.Builder<String, WLCommand> WLCommandMapBuilder = ImmutableMultimap.builder();

		// create(Map<WLCommandComponent, Integer> commandsCountMap)
		// The words go in order they are expected to appear in sentence.
		// In int array, 1 means to use in posList in final command, 0 means
		// don't use.
		// eg "of" in "element of" is not used, but should be there to determine
		// if a command is satisfied.
		// all Strings go into commandsMap and PosList, but only those with true
		// goes to final command String built
		// "symb|ent" "pre, of" "symb|ent"; All regexes to be matched
		// type and name are always specified, if name left empty, will become
		// wildcard.
		// type and name uniquely specify a WLCommand, for the same command, use
		// custom position if it's specified
		// (by an int, 4 comma-separated strings total), use default order
		// otherwise (3 comma-separated strings).
		// name being -1 indicates WL command.
		// -1 indicates WL command.
		// indicate trigger word in sentence, if not WL command. Ensure position
		// of trigger word is correct.
		// Just a String represents an auxilliary String, eg just a bracket.
		// 3rd element could be true/false (includes/not includes in command),
		// or trigger (trigger word, but not
		// included in final command, so indicates false)
		// 5th element used for defining custom order for now, 4th term defines
		// whether to trigger TriggerMathObj term
		WLCommandMapBuilder.put("element", addCommand(
				new String[] { "symb|ent, , true", "\\[Element], WL, true", "pre, of, false", "symb|ent, , true" }));		
		WLCommandMapBuilder.put("element", addCommand(
				new String[] { "parti, , false", ", element, trigger", "x", "\\[Element]", "pre, of, false", "symb|ent, , true" }));
		WLCommandMapBuilder.put("exist", addCommand(
				new String[] { ", there, false", "Exists[", ", exists*, trigger",  "ent|symb|phrase|noun, , true", "]"}));
		WLCommandMapBuilder.put("derivative",
				addCommand(new String[] { "Derivative, WL, true", "[", "pre, of, false", "symb|ent, , true", "]" }));
		WLCommandMapBuilder.put("log",
				addCommand(new String[] { "Log, WL, true", "[", "pre, of, false", "symb|ent, , true", "]" }));
		WLCommandMapBuilder.put("union",
				addCommand(new String[] { "Union, WL, true", "[", "pre, of, false", "symb|ent, , true", "]" }));
		// label a term to use to trigger a mathObj, communicate to posList,
		// posList dynamically builds command
		// using TriggerMathObj.
		//WLCommandMapBuilder.put("is", addCommand(new String[] { "symb|ent, , true", "verb|vbs, is|are|be, trigger",
			//	"\\[Element]", "symb|ent, , true" }));
		WLCommandMapBuilder.put("subset",
				addCommand(new String[] { "Subset, WL, true", "[", "pre, of, false", "symb|ent, , true", "]" }));
		// $f=\sum i$ with radius of convergence $r$
		WLCommandMapBuilder.put("convergence", addCommand(new String[] { "symb|ent, , true", "\\subset",
				", radius, false", ", convergence, trigger", "Function[ 'radius' ", "]" }));
		
		// trigger TriggerMathObj
		WLCommandMapBuilder.put("is", addCommand(new String[] { "symb|ent, , true", "verb|vbs, is|are|be, trigger",
				"\\[Element]", "symb|ent|adj, , true, TriggerMathObj" }));
		
		WLCommandMapBuilder.put("at most", addCommand(new String[] { "symb|ent, , true", "verb|vbs, is|are|be, false",
				"<=", "pre, at most, trigger", "symb|ent, , true, TriggerMathObj" }));
		// label string if to be used as trigger ent/symb, then use these words
		// as trigger system
		// function with radius of convergence

		// logical operators
		WLCommandMapBuilder.put("and",
				addCommand(new String[] { "Subset, WL, true", "[", "pre, of, false", "symb|ent, , true", "]" }));

		WLCommandMap = WLCommandMapBuilder.build();

	}

	/**
	 * Create WLCommands using input data and add them to an immutable list.
	 * Specifically, create commandsCountMap with WLCommandComponent and
	 * supplied integer.
	 * 
	 * @param commandStringAr
	 *            Array of command Strings
	 */
	///// name should be there already!
	public static WLCommand addCommand(String[] commandStringAr) {
		ImmutableMap<WLCommandComponent, Integer> commandsCountMap;
		// used to build commandsCountMap
		Map<WLCommandComponent, Integer> commandsCountPreMap = new HashMap<WLCommandComponent, Integer>();

		List<PosTerm> posList = new ArrayList<PosTerm>();
		// total number of components, it's sum of all entries in
		// ComponentCountMap,
		// minus the number of WL commands.
		int componentCounter = 0;
		// assert(commandStringAr.length == useAr.length);
		int triggerWordIndex = -1;

		for (int i = 0; i < commandStringAr.length; i++) {

			// those are regexes to be matched
			String commandStr = commandStringAr[i];

			String[] commandStrParts = commandStr.split(",");

			// auxilliary String like brackets. Just put in posTermList, don't
			// put in commandsMap
			if (commandStrParts.length == 1) {
				String posStr = commandStrParts[0];
				String nameStr = "AUX"; // auxilliary string
				WLCommandComponent commandComponent = new WLCommandComponent(posStr, nameStr);
				PosTerm curTerm = new PosTerm(commandComponent, AUXINDEX, true, false);
				posList.add(curTerm);
			} else {

				String posStr = commandStrParts[0].matches("\\s*") ? ".*" : commandStrParts[0].trim();
				// String nameStr = commandStrParts.length > 2 ?
				// commandStrParts[1] : "*";
				String nameStr = commandStrParts[1].matches("\\s*") ? ".*" : commandStrParts[1].trim();

				// int toUse = commandStrParts.length > 2 ?
				// Integer.valueOf(commandStrParts[2]) :
				// Integer.valueOf(commandStrParts[1]);
				boolean useInPosList = Boolean.valueOf(commandStrParts[2].trim());
				boolean triggerMathObj = false;
				// process command and create WLCommandComponent and PosList
				WLCommandComponent commandComponent = new WLCommandComponent(posStr, nameStr);

				// how many have we added so far
				Integer temp;
				int curOcc = (temp = commandsCountPreMap.get(commandComponent)) == null ? 0 : temp;
				
				int positionInMap = curOcc;
				// check length of commandStrParts to see if custom order is
				// required
				if (commandStrParts.length > 3) {
					//if trigger triggerMathObj
					triggerMathObj = commandStrParts[3].trim().equals(TRIGGERMATHOBJSTR);
					if (commandStrParts.length > 4) {
						positionInMap = Integer.valueOf(commandStrParts[4]);
					}
				}
				// check if WL command, ie if name is "-2", in which case put -2
				// as posInMap in PosTerm
				else if (nameStr.matches("WL.*")) {
					positionInMap = WLCOMMANDINDEX;
					componentCounter--;
					//triggerWordIndex hasn't been touched
					if(triggerWordIndex == -1){
						triggerWordIndex = i;
					}
					
				}

				if (commandStrParts[2].trim().matches("trigger")) {
					triggerWordIndex = i;
					componentCounter--;
				}
				
				// curOcc is the position inside the list in commandsMap.
				// but sometimes want to switch order of occurence in final
				// command and order
				// in original sentence
				PosTerm curTerm = new PosTerm(commandComponent, positionInMap, useInPosList, triggerMathObj);
				posList.add(curTerm);
				// }
				// PosTerm(WLCommandComponent commandComponent, int position,
				// boolean includeInBuiltString){

				commandsCountPreMap.put(commandComponent, curOcc + 1);
				componentCounter++;
			}
		}

		//shouldn't be invoked, as every command should have a trigger word
		if(triggerWordIndex == -1){
			triggerWordIndex = 0;
		}
		
		commandsCountMap = ImmutableMap.copyOf(commandsCountPreMap);
		return WLCommand.create(commandsCountMap, posList, componentCounter, triggerWordIndex);
		
	}

	/**
	 * 
	 * @return ImmutableMap WLCommandMap
	 */
	public static Multimap<String, WLCommand> WLCommandMap() {
		return WLCommandMap;
	}

	/**
	 * @return ImmutableMap triggerWordLookupMap
	 */
	public static Multimap<String, String> triggerWordLookupMap() {
		return triggerWordLookupMap;
	}
}
