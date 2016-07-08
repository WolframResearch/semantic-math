package thmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

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
	private static ImmutableMap<String, WLCommand> WLCommandMap;
	
	/**
	 * should read in data from file instead of calling addCommand.
	 * Need 
	 */
	static{
		//create(Map<WLCommandComponent, Integer> commandsCountMap)
		//The words go in order they are expected to appear in sentence.
		//In int array, 1 means to use in posList in final command, 0 means don't use.
		//eg "of" in "element of" is not used, but should be there to determine if a command is satisfied.
		//all Strings go into commandsMap, but only those with 1 goes to PosList
		//"symb|ent" "pre, of" "symb|ent"
		addCommand(new String[]{"symb|ent", "pre, of", "symb|ent"},
				new int[]{1, 0, 1}, "element");		
		
	}
	
	/**
	 * Create WLCommands using input data and add them to an immutable list.
	 * Specifically, create commandsCountMap with WLCommandComponent and supplied integer. 
	 * @param 
	 */
	/////name should be there!
	public static void addCommand(String[] posStringAr, String[] nameStringAr, int[] useAr, String triggerWord){
		ImmutableMap<WLCommandComponent, Integer> commandsCountMap;
		Map<WLCommandComponent, Integer> map  = new HashMap<WLCommandComponent, Integer>();
		assert(posStringAr.length == useAr.length);
		assert(nameStringAr.length == useAr.length);
		
		for(int i = 0; i < posStringAr.length; i++){
			//those are regexes to be matched
			String posStr = posStringAr[i];
			String nameStr = posStringAr[i];
			int toUse = useAr[i];
			//process command and create WLCommandComponent and PosList
			WLCommandComponent commandComponent = new WLCommandComponent(posStr, nameStr);
			List<PosTerm> posList = new ArrayList<PosTerm>();
			int curOcc = map.get(commandComponent);
			
			if(toUse == 1){
				int posInMap = curOcc; ///////////////
				PosTerm curTerm = new PosTerm(commandComponent, curOcc);
				posList.add(curTerm);
			}			
			
			map.put(commandComponent, curOcc+1);
			
		}
		
		commandsCountMap = ImmutableMap.copyOf(map);
	}
	
}
