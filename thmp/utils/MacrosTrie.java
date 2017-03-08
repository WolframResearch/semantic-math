package thmp.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 *
 */
public class MacrosTrie/*<MacrosTrieNode> extends WordTrie<WordTrieNode>*/ {

	private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
	MacrosTrieNode rootNode;
	//WordTrie<MacrosTrieNode> trie = new MacrosTrie();
	private static final Logger logger = LogManager.getLogger(MacrosTrie.class);
	
	public static class MacrosTrieNode /*extends WordTrieNode*/{
		//e.g. \xra
		String commandStr;
		//e.g. \xrightarrow, or \\underline{#1}
		String replacementStr;
		int slotCount;
		Map<Character, MacrosTrieNode> nodeMap;
		
		public MacrosTrieNode(String commandStr_, String replacementStr_){
			this.commandStr = commandStr_;
			this.replacementStr = replacementStr_;
			int slotCount = 0;
			for(int i = 0; i < replacementStr_.length()-1; i++){
				if(replacementStr_.charAt(i) == '#' && DIGIT_PATTERN.matcher(String.valueOf(replacementStr_.charAt(i+1))).matches()){
					slotCount++;
					i++;
				}
			}
			this.slotCount = slotCount;
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
		
		/**
		 * A node entry should not already exist, since no overloading
		 * of macros names is allowed.
		 * If entry already exists, leave original one intact.
		 * @param c
		 * @param node
		 */
		public void addTrieNodeToNodeMap(char c, String commandStr_, String replacementStr_){
			MacrosTrieNode node = this.nodeMap.get(c);
			if(null != node){
				if(null != node.commandStr && null != node.replacementStr){
					logger.error("Command already exists!");
					return;
				}
				node.setCommandAndReplacementStr(commandStr_, replacementStr_);				
			}else{
				this.nodeMap.put(c, new MacrosTrieNode(commandStr_, replacementStr_));
			}			
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
		this.rootNode = new MacrosTrieNode("", "");
		
	}
	
	/**
	 * Walk down the trie and adds the TrieNode where appropriate.
	 * @param c
	 * @param node
	 */
	public void addTrieNode(String commandStr, String replacementStr){
		
		MacrosTrieNode curNode = this.rootNode;
		char c;
		int commandStrLen = commandStr.length();
		for(int i = 0; i < commandStrLen-1; i++){
			c = commandStr.charAt(i);
			curNode = curNode.addOrRetrievePassingNode(c);			
		}
		c = commandStr.charAt(commandStrLen-1);
		curNode.addTrieNodeToNodeMap(c, commandStr, replacementStr);		
	}
	
	public String replaceMacros(String thmStr){
		
		List<MacrosTrieNode> trieNodeList = new ArrayList<MacrosTrieNode>();
		trieNodeList.add(this.rootNode);
		StringBuilder thmSB = new StringBuilder();
		
		int thmStrLen = thmStr.length();		
		for(int i = 0; i < thmStrLen; i++){
			char c = thmStr.charAt(i);
			int jump = 0;
			String replacementStr;
			Iterator<MacrosTrieNode> trieNodeListIter = trieNodeList.iterator();
			while(trieNodeListIter.hasNext()){
				MacrosTrieNode curNode = trieNodeListIter.next();
				MacrosTrieNode nextNode = curNode.getTrieNode(c);
				if(null == nextNode){
					trieNodeListIter.remove();
					continue;
				}else if(null == (replacementStr=nextNode.replacementStr)){
					continue;
				}else {
					//form replacement String
					jump = formReplacementString(thmStr, i+1, replacementStr);
					
				}
				
			}
			if(jump > 0){
				
			}
		}
		return thmStr;
	}

	/**
	 * Return how many places to skip ahead.
	 * @param thmStr
	 * @param i
	 * @param replacementStr
	 * @return
	 */
	private int formReplacementString(String thmStr, int curIndex, MacrosTrieNode trieNode, StringBuilder replacementSB) {
		//replace #...		
		int indexToSkip = 0;
		int slotCount = trieNode.slotCount;
		String[] args = new String[slotCount];
		int thmStrLen = thmStr.length();
		StringBuilder argsReplacementSB = new StringBuilder(10);
		//capture the arguments
		for(int i = curIndex; i < thmStrLen; i++){
			char c = thmStr.charAt(i);
			while(c != '{'){
				i++;
			}
			i++;
			if(i >= thmStrLen){
				return 0;
			}
			String s = retrieveBracesContent(thmStr, i);
			
			
		}
		return 0;
	}

	private String retrieveBracesContent(String thmStr, int i) {
		char c;
		while(i < thmStr.length() && (c=thmStr.charAt(i)) != '}'){
			//what if braces don't all close?? try out tex!
			if(i){
				
			}
		}
		
		return null;
	}
	
}
