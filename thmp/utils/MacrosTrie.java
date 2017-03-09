package thmp.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.utils.WordTrie.WordTrieNode;

/**
 * Trie for custom macros commands.
 * \newcommand{\xra}  {\xrightarrow}
 * \newcommand{\\un}[1]{\\underline{#1}}
 * Should use a Builder to build MacrosTrie, and make it immutable!
 * @author yihed
 */
public class MacrosTrie/*<MacrosTrieNode> extends WordTrie<WordTrieNode>*/ {

	private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d)");
	MacrosTrieNode rootNode;
	//WordTrie<MacrosTrieNode> trie = new MacrosTrie();
	private static final Logger logger = LogManager.getLogger(MacrosTrie.class);
	private static final Map<String, String> commandAndReplacementStrMap = new HashMap<String, String>();
	private static final Integer CAPTURING_GROUP_SHIFT = 1;
	
	public static class MacrosTrieNode /*extends WordTrieNode*/{
		//e.g. \xra
		String commandStr;
		//e.g. \xrightarrow, or \\underline{#1}
		String replacementStr;
		int slotCount;
		Map<Character, MacrosTrieNode> nodeMap;
		
		public MacrosTrieNode(String commandStr_, String replacementStr_, int slotCount_){
			this.commandStr = commandStr_;
			this.replacementStr = replacementStr_;
			this.slotCount = slotCount_;
			this.nodeMap = new HashMap<Character, MacrosTrieNode>();
		}
		
		public MacrosTrieNode(){
			this.nodeMap = new HashMap<Character, MacrosTrieNode>();
		}
		
		public void setCommandAndReplacementStr(String commandStr_, String replacementStr_){
			this.commandStr = commandStr_;
			this.replacementStr = replacementStr_;
		}
		
		public MacrosTrieNode getTrieNode(char c){
			return nodeMap.get(c);
		}
		
		public String getReplacementStr(){
			return this.replacementStr;
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder(30);
			return sb.append(this.commandStr).append(" ").append(this.replacementStr).append(" ").append(this.slotCount).toString();
			
		}
		/**
		 * A node entry should not already exist, since no overloading
		 * of macros names is allowed.
		 * If entry already exists, leave original one intact.
		 * @param c
		 * @param node
		 */
		public void addTrieNodeToNodeMap(char c, String commandStr_, String replacementStr_, int slotCount_){
			MacrosTrieNode node = this.nodeMap.get(c);
			if(null != node){
				if(null != node.commandStr && null != node.replacementStr){
					logger.error("Command already exists!");
					return;
				}
				node.setCommandAndReplacementStr(commandStr_, replacementStr_);				
			}else{
				this.nodeMap.put(c, new MacrosTrieNode(commandStr_, replacementStr_, slotCount_));
			}	
			commandAndReplacementStrMap.put(commandStr_, replacementStr_);
		}		
		
		/**
		 * Add or retrieve a node with char c in the middle of a commandStr.
		 * @param c
		 * @return
		 */
		public MacrosTrieNode addOrRetrievePassingNode(char c){
			MacrosTrieNode node = this.nodeMap.get(c);
			if(null == node){
				node = new MacrosTrieNode();
				this.nodeMap.put(c, node);
			}			
			return node;
		}
		
		public Map<Character, MacrosTrieNode> nodeMap(){
			return this.nodeMap;
		}		
	}
	
	public MacrosTrie( ){
		this.rootNode = new MacrosTrieNode("", "", 0);
		
	}
	
	/**
	 * Walk down the trie and adds the TrieNode where appropriate.
	 * @param c
	 * @param node
	 */
	public void addTrieNode(String commandStr, String replacementStr, int slotCount){
		
		MacrosTrieNode curNode = this.rootNode;
		char c;
		int commandStrLen = commandStr.length();
		for(int i = 0; i < commandStrLen-1; i++){
			c = commandStr.charAt(i);
			curNode = curNode.addOrRetrievePassingNode(c);			
		}
		c = commandStr.charAt(commandStrLen-1);
		curNode.addTrieNodeToNodeMap(c, commandStr, replacementStr, slotCount);		
	}
	
	/**
	 * @param thmStr
	 * @return thmStr with macros replaced with macros defined in this MacrosTrie.
	 */
	public String replaceMacrosInThmStr(String thmStr){
		
		List<MacrosTrieNode> trieNodeList = new ArrayList<MacrosTrieNode>();
		//trieNodeList.add(this.rootNode);
		StringBuilder thmSB = new StringBuilder();
		
		String commandStrTriggered = "";
		StringBuilder commandStrSB = new StringBuilder(20);
		int thmStrLen = thmStr.length();
		
		for(int i = 0; i < thmStrLen; i++){
			char c = thmStr.charAt(i);	
			thmSB.append(c);
			//String replacementStr;
			//Iterator<MacrosTrieNode> trieNodeListIter = trieNodeList.iterator();			
			//add all newly-triggered commands to nodeList
			if(this.rootNode.nodeMap.containsKey(c)){
				trieNodeList.add(this.rootNode);
			}
			
			for(int j = 0; j < trieNodeList.size(); j++){
				//MacrosTrieNode curNode = trieNodeListIter.next();
				MacrosTrieNode curNode = trieNodeList.get(j);
				if(null == curNode) continue;
				MacrosTrieNode nextNode = curNode.getTrieNode(c);
				if(null == nextNode){
					//node cannot correspond to any known macro
					//trieNodeListIter.remove();
					trieNodeList.set(j, null);
					continue;
				}else if(null == nextNode.replacementStr){	
					trieNodeList.set(j, nextNode);
					continue;
				}else {
					//substitute with replacement String.
					int nextStartingIndex = formReplacementString(thmStr, i, nextNode, commandStrSB); //HERE too long
					//to counter the i++ in the loop.
					i = nextStartingIndex-1;
					commandStrTriggered = nextNode.commandStr;
					//trieNodeListIter.remove();
					//System.out.println("commandStrSB " + commandStrSB);
					//don't clear trieNodeList to allow for nested macros.
					break;
				}
			}
			
			if(commandStrSB.length() > 0){				
				int thmSBLen = thmSB.length();
				thmSB.delete(thmSBLen - commandStrTriggered.length(), thmSBLen);
				thmSB.append(commandStrSB);
				commandStrSB.setLength(0);
				trieNodeList = new ArrayList<MacrosTrieNode>();
			}
		}
		return thmSB.toString();
	}

	/**
	 * Return how many places to skip ahead. Look for args inside braces {...}
	 * @param thmStr
	 * @param i
	 * @param replacementSB SB to be filled in.
	 * @return updated index (in original thmStr) to start further examination at.
	 */
	private int formReplacementString(String thmStr, int curIndex, MacrosTrieNode trieNode, StringBuilder replacementSB
			) {
		//replace #...		
		//int indexToSkip = 0;
		int slotCount = trieNode.slotCount;
		String templateReplacementString = trieNode.replacementStr;
		if(slotCount == 0){
			replacementSB.append(templateReplacementString);
			return curIndex;
		}
		
		String[] args = new String[slotCount];
		
		//capture the arguments, fill in args, one at a time;
		int startingIndex = curIndex;
		for(int i = 0; i < slotCount; i++){
			startingIndex = retrieveBracesContent(thmStr, startingIndex, args, i);
		}		
		//System.out.println("args: " + Arrays.toString(args));
		int templateReplacementStringLen = templateReplacementString.length();
		Matcher digitMatcher;
		//fill in #i with its respective replacement string.
		int lastSlotEndIndex = 0;
		int i;
		for(i = 0; i < templateReplacementStringLen-1; i++){
			
			char c = templateReplacementString.charAt(i);			
			if(c == '#' && (digitMatcher=DIGIT_PATTERN.matcher(String.valueOf(templateReplacementString.charAt(i+1)))).matches()){
				
				int slotArgNum = Integer.valueOf(digitMatcher.group(1)) - CAPTURING_GROUP_SHIFT;
				//System.out.println("slotArgNum "+slotArgNum);
				//should not occur if valid latex syntax.
				if(slotArgNum >= slotCount || slotArgNum < 0){
					logger.error("latex syntax error: slotArgNum >= slotCont || slotArgNum < 1 for thm: " + thmStr);
					continue;
				}				
				replacementSB.append(args[slotArgNum]);
				//System.out.println("args[slotArgNum] " + args[slotArgNum]);
				i++;
				lastSlotEndIndex = i;
				//slotCounter++;
			}else{
				replacementSB.append(c);				
			}
		}
		if(lastSlotEndIndex < templateReplacementStringLen-1){
			replacementSB.append(templateReplacementString.charAt(templateReplacementStringLen-1));
		}
		//System.out.println("replacementSB " + replacementSB + " templateReplacementString "+ templateReplacementString);
		
		return startingIndex;
	}

	/**
	 * Retrieves the content in braces down the stream.
	 * @param thmStr
	 * @param index
	 * @param bracesArgs array of args to be filled in for #1
	 * @param bracesArgsIndex index to be filled in, guaranteed to be <= bracesArgs.length
	 * @return the starting position to keep looking.
	 */
	private int retrieveBracesContent(String thmStr, int index, String[] bracesArgs, int bracesArgsIndex) {
		
		assert bracesArgsIndex < bracesArgs.length;
		
		int thmStrLen = thmStr.length();
		char c;
		int i = index;
		
		while(i < thmStrLen && ((c=thmStr.charAt(i)) != '{')){
			i++;
		}
		//skipe brace, so openBraceCount is 1
		i++;
		int openBraceCount = 1;
		if(i >= thmStrLen){
			return index;
		}		
		char prevChar = ' ';
		StringBuilder braceContentSB = new StringBuilder(15);
		
		while(i < thmStr.length() && ((c=thmStr.charAt(i)) != '}' || prevChar == '\\' || openBraceCount>0 ) ){
			/* if braces don't all match, compile error. Unless escaped.*/	
			if(prevChar != '\\'){
				if(c == '{'){
					openBraceCount++;
				}else if(c == '}'){
					openBraceCount--;
				}			
			}
			i++;
			if(openBraceCount == 0){
				break;
			}
			braceContentSB.append(c);
			prevChar = c;			
		}
		//System.out.println("thmStr.charAt(i): " + thmStr.charAt(i));
		bracesArgs[bracesArgsIndex] = braceContentSB.toString();
		return i;
	}
	
	@Override
	public String toString(){
		return commandAndReplacementStrMap.toString();
		//return this.rootNode.toString();
	}
	
}
