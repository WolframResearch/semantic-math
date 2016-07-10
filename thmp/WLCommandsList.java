package thmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import thmp.WLCommand.PosTerm;
import thmp.WLCommand.WLCommandComponent;

/**
 * list of WLCommand's.
 * Creates WLCommands using data here and stores them in an immutable Multimap, keys being trigger words.
 *
 */
public class WLCommandsList {
	
	/**
	 * Map, keys are trigger words, values are pointers to WLCommand's
	 */
	private static ImmutableMultimap<String, WLCommand> WLCommandMap; //need builder for this in static initializer
	
	/**
	 * should read in data from file instead of calling addCommand.
	 * Need 
	 */
	static{
		//builder for WLComamndMap, 
		ImmutableMultimap.Builder<String, WLCommand> WLCommandMapBuilder = ImmutableMultimap.builder();
		
		//create(Map<WLCommandComponent, Integer> commandsCountMap)
		//The words go in order they are expected to appear in sentence.
		//In int array, 1 means to use in posList in final command, 0 means don't use.
		//eg "of" in "element of" is not used, but should be there to determine if a command is satisfied.
		//all Strings go into commandsMap and PosList, but only those with true goes to final command String built
		//"symb|ent" "pre, of" "symb|ent"; All regexes to be matched
		//type and name are always specified, if name left empty, will become wildcard. 
		//type and name uniquely specify a WLCommand, for the same command, use custom position if it's specified 
		//(by an int, 4 comma-separated strings total), use default order otherwise (3 such strings). 
		//name being -1 indicates WL command.
		//-1 indicates WL command
		WLCommandMapBuilder.put("element", addCommand(new String[]{"symb|ent, , true", "\\[Element], -1, true", 
				"pre, of, false", "symb|ent, , true"}));		
		
		WLCommandMap = WLCommandMapBuilder.build();
	}
	
	/**
	 * Create WLCommands using input data and add them to an immutable list.
	 * Specifically, create commandsCountMap with WLCommandComponent and supplied integer. 
	 * @param 
	 */
	/////name should be there already!
	public static WLCommand addCommand(String[] commandStringAr){
		ImmutableMap<WLCommandComponent, Integer> commandsCountMap;
		//used to build commandsCountMap
		Map<WLCommandComponent, Integer> commandsCountPreMap  = new HashMap<WLCommandComponent, Integer>();		
		
		List<PosTerm> posList = new ArrayList<PosTerm>();
		//total number of components, it's sum of all entries in ComponentCountMap,
		//minus the number of WL commands.
		int componentCounter = 0;
		//assert(commandStringAr.length == useAr.length);
		int triggerWordIndex = 0;
		
		for(int i = 0; i < commandStringAr.length; i++){
			
			//those are regexes to be matched			
			String commandStr = commandStringAr[i];
			
			String[] commandStrParts = commandStr.split(",") ;
			
			String posStr = commandStrParts[0].equals("") ? "." : commandStrParts[0];
			//String nameStr = commandStrParts.length > 2 ? commandStrParts[1] : "*";
			String nameStr = commandStrParts[1].equals("") ? ".*" : commandStrParts[1];
			
			//int toUse = commandStrParts.length > 2 ? Integer.valueOf(commandStrParts[2]) : Integer.valueOf(commandStrParts[1]);
			boolean useInPosList = Boolean.valueOf(commandStrParts[2]);
			
			//process command and create WLCommandComponent and PosList
			WLCommandComponent commandComponent = new WLCommandComponent(posStr, nameStr);
			
			//how many have we added so far
			Integer temp;
			int curOcc = (temp=commandsCountPreMap.get(commandComponent)) == null ? 0 : temp;
			
			//if(useInPosList){
				int positionInMap = curOcc;
				//check length of commandStrParts to see if custom order is required
				if(commandStrParts.length > 3){
					positionInMap = Integer.valueOf(commandStrParts[3]);
				}				
				//check if WL command, ie if name is "-1", in which case put -1 as 
				//posInMap in PosTerm
				else if(Integer.valueOf(nameStr) == -1){
					positionInMap = -1;
					componentCounter--;
					triggerWordIndex = i;
				}
				
				//curOcc is the position inside the list in commandsMap.
				//but sometimes want to switch order of occurence in final command and order 
				//in original sentence
				PosTerm curTerm = new PosTerm(commandComponent, positionInMap, useInPosList);
				posList.add(curTerm);
			//}			
			
			commandsCountPreMap.put(commandComponent, curOcc+1);
			componentCounter++;
		}
		
		commandsCountMap = ImmutableMap.copyOf(commandsCountPreMap);
		return WLCommand.create(commandsCountMap, posList, componentCounter, triggerWordIndex);
		
	}
	
	/**
	 * 
	 * @return	ImmutableMap
	 */
	public static Multimap<String, WLCommand> WLCommandMap(){
		return WLCommandMap;
	}
}
