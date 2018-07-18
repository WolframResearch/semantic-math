package thmp.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableMap;

/**
 * Trie for custom macros commands.
 * E.g.:
 * \newcommand{\xra}  {\xrightarrow}
 * \newcommand{\\un}[1]{\\underline{#1}}
 * \def\X{{\cal X}}
 * Use a Builder to build MacrosTrie. MacrosTrie is immutable.
 * @author yihed
 */
public class MacrosTrie/*<MacrosTrieNode> extends WordTrie<WordTrieNode>*/ {

	private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d)");
	MacrosTrieNode rootNode;
	private static final Logger logger = LogManager.getLogger(MacrosTrie.class);
	private static final Map<String, String> commandAndReplacementStrMap = new HashMap<String, String>();
	private static final Integer CAPTURING_GROUP_SHIFT = 1;
	
	
	public static class MacrosTrieBuilder{
		
		MacrosTrieNode rootNode;

		public MacrosTrieBuilder(){
			this.rootNode = new MacrosTrieNode("", "", 0);		
		}
		
		public MacrosTrie build(){
			return new MacrosTrie(this);
		}
		
		/**
		 * Walk down the trie and adds the TrieNode where appropriate.
		 * @param c
		 * @param node
		 * @param slotCount E.g. the count #1 in \newcommand{\\un}[1]{\\underline{#1}}. 
		 * slotCount = 0 if no count present.
		 * @param optArgDefault: default value for optional arg, usually for arg #1.
		 */
		public void addTrieNode(String commandStr, String replacementStr, int slotCount, String...optArgDefault){
			
			MacrosTrieNode curNode = this.rootNode;
			char c;
			int commandStrLen = commandStr.length();
			for(int i = 0; i < commandStrLen-1; i++){
				c = commandStr.charAt(i);
				curNode = curNode.addOrRetrievePassingNode(c);			
			}
			c = commandStr.charAt(commandStrLen-1);
			curNode.addTrieNodeToNodeMap(c, commandStr, replacementStr, slotCount, optArgDefault);		
		}
		
	}
	
	public static class MacrosTrieNode /*extends WordTrieNode*/{
		//e.g. \xra
		private String commandStr;
		//e.g. \xrightarrow, or \\underline{#1}
		private String replacementStr;
		private int slotCount;
		//default value for optional argument, only applicable for some commands,
		//e.g. newcommand{cmd}[2][default]{a}
		private String optArgVal;
		private Map<Character, MacrosTrieNode> nodeMap;
		
		public static class ImmutableMacrosTrieNode extends MacrosTrieNode{
			
			public ImmutableMacrosTrieNode(MacrosTrieNode trieNode, 
					ImmutableMap<Character, MacrosTrieNode> immutableNodeMap){
				super.commandStr = trieNode.commandStr;
				super.replacementStr = trieNode.replacementStr;
				super.slotCount = trieNode.slotCount;
				super.optArgVal = trieNode.optArgVal;
				/*make map immutable*/
				super.nodeMap = immutableNodeMap;
			}			
			
			@Override
			public void setCommandAndReplacementStr(String commandStr_, String replacementStr_){			
				throw new UnsupportedOperationException("ImmutableMacrosTrieNode cannot be modified!");
			}
			
			@Override
			public void addTrieNodeToNodeMap(char c, String commandStr_, String replacementStr_, int slotCount_,
					String...optArgDefault_){
				throw new UnsupportedOperationException("ImmutableMacrosTrieNode cannot be modified!");
			}
			
			@Override
			public MacrosTrieNode addOrRetrievePassingNode(char c){
				throw new UnsupportedOperationException("ImmutableMacrosTrieNode cannot be modified!");
			}			
		}/*End of ImmutableMacrosTrieNode Class*/
		
		public MacrosTrieNode(String commandStr_, String replacementStr_, int slotCount_){
			this.commandStr = commandStr_;
			this.replacementStr = replacementStr_;
			this.slotCount = slotCount_;
			this.nodeMap = new HashMap<Character, MacrosTrieNode>();
		}
		
		public MacrosTrieNode(String commandStr_, String replacementStr_, int slotCount_, String optArgDefault_){
			this(commandStr_, replacementStr_, slotCount_);
			this.optArgVal = optArgDefault_;
		}
		
		/**
		 * Take node, make the maps in it and its descendents immutable.
		 * @param trieNode
		 * @return An immutable MacrosTrieNode.
		 */
		public MacrosTrieNode makeNodeImmutable(){
			ImmutableMap.Builder<Character, MacrosTrieNode> mapBuilder 
				= new ImmutableMap.Builder<Character, MacrosTrieNode>();
			for(Entry<Character, MacrosTrieNode> entry : this.nodeMap.entrySet()){
				MacrosTrieNode childNode = entry.getValue();
				/*make immutable childNode */
				childNode = childNode.makeNodeImmutable();
				mapBuilder.put(entry.getKey(), childNode);
			}			
			return new ImmutableMacrosTrieNode(this, mapBuilder.build());
		}
		
		public MacrosTrieNode(){
			this.nodeMap = new HashMap<Character, MacrosTrieNode>();
		}
		
		public void setCommandAndReplacementStr(String commandStr_, String replacementStr_){
			this.commandStr = commandStr_;
			this.replacementStr = replacementStr_;
		}
		/**
		 * @param c
		 * @return null if nodeMap does not contain a TrieNode corresponding to c
		 */
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
		public void addTrieNodeToNodeMap(char c, String commandStr_, String replacementStr_, int slotCount_, String...optArgDefaultAr){
			MacrosTrieNode node = this.nodeMap.get(c);
			String optArgDefault = null;
			if(optArgDefaultAr.length > 0) {
				optArgDefault = optArgDefaultAr[0];
			}
			if(null != node){
				if(null != node.commandStr && null != node.replacementStr){
					logger.info(node.commandStr + " command already exists!");
					return;
				}
				node.setCommandAndReplacementStr(commandStr_, replacementStr_);				
			}else{
				this.nodeMap.put(c, new MacrosTrieNode(commandStr_, replacementStr_, slotCount_, optArgDefault));
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
	
	/**
	 * Create an immutable MacrosTrie.
	 * @param builder
	 */
	private MacrosTrie(MacrosTrieBuilder builder){
		this.rootNode = builder.rootNode.makeNodeImmutable();
	}	
	
	/**
	 * Used during data extraction process after trie is built.
	 * @param thmStr
	 * @return thmStr with macros replaced with macros defined in this MacrosTrie.
	 */
	public String replaceMacrosInThmStr(String thmStr){
		return replaceMacrosInThmStr(thmStr, true);
	}
	
	/**
	 * Used during data extraction process after trie is built.
	 * @param thmStr
	 * @param checkNested: whether to check for nested macros, e.g. \newcommand{\s}{\sp_e}
	 * @return thmStr with macros replaced with macros defined in this MacrosTrie.
	 */
	private String replaceMacrosInThmStr(String thmStr, boolean checkNested){
		
		List<MacrosTrieNode> trieNodeList = new ArrayList<MacrosTrieNode>();
		
		StringBuilder thmSB = new StringBuilder();
		
		String commandStrTriggered = "";
		StringBuilder commandStrSB = new StringBuilder(20);
		int thmStrLen = thmStr.length();
		
		for(int i = 0; i < thmStrLen; i++){
			char c = thmStr.charAt(i);	
			thmSB.append(c);
						
			//add all newly-triggered commands to nodeList
			if(this.rootNode.nodeMap.containsKey(c)){
				trieNodeList.add(this.rootNode);
			}
			int futureIndex = i;
			for(int j = 0; j < trieNodeList.size(); j++){
				MacrosTrieNode curNode = trieNodeList.get(j);
				if(null == curNode) continue;
				int thmSBLen = thmSB.length();
				MacrosTrieNode nextNode = curNode.getTrieNode(c);
				if(null == nextNode || (nextNode.commandStr != null 
						&& thmSB.charAt(thmSBLen - nextNode.commandStr.length()) != '\\')){
					//node cannot correspond to any known macro
					trieNodeList.set(j, null);
					continue;
				}else if(null == nextNode.replacementStr){	
					trieNodeList.set(j, nextNode);
					continue;
				}else{
					//go down to see if a longer command can be satisfied
					int k = i+1;
					MacrosTrieNode futureNode = null;
					MacrosTrieNode runningNode = nextNode;					
					while(k < thmStrLen && null != (runningNode = runningNode.getTrieNode(thmStr.charAt(k)))){
						if(null != runningNode.commandStr && null != runningNode.replacementStr){
							int runningNodeCommandStrLen = runningNode.commandStr.length();
							//+1 because runningNodeCommandStrLen includes the slash, e.g. \eq, and thmSB
							//does not include char at index k yet.
							int slashIndex = k - runningNodeCommandStrLen + 1;
							//thmStr and not thmSB, since thmSB
							if(slashIndex > -1 && thmStr.charAt(slashIndex) == '\\') {								
								futureNode = runningNode;
								futureIndex = k;	
							}
						}
						k++;
					}
					
					if(null != futureNode){
						//The remaining part of the commandStr. i+1, because k started at i+1 when searching for futureNode
						String longerCommandStr = thmStr.substring(i+1, futureIndex+1);
						thmSB.append(longerCommandStr);	
						nextNode = futureNode;
						i = futureIndex;	
					}
					//form the replacement String. Returns updated index (in original thmStr) to start further examination.
					futureIndex = formReplacementString(thmStr, futureIndex, nextNode, commandStrSB, checkNested);	
					
					commandStrTriggered = nextNode.commandStr;
					//don't clear trieNodeList to allow for nested macros.
					break;
				}
			}
			/*e.g. don't turn \label into \lambdable*/
			if(commandStrSB.length() > 0){
				String nextCharStr = "";
				if(futureIndex < thmStrLen-1){
					nextCharStr = String.valueOf(thmStr.charAt(futureIndex+1));
				}
				int commandStrTriggeredLen = commandStrTriggered.length();
				int thmSBLen = thmSB.length();
				//don't want to replace if not full command. E.g. don't replace "a" with {a} in \omega. 
				if(!WordForms.ALPHABET_PATTERN.matcher(nextCharStr).matches() && thmSB.charAt(thmSBLen - commandStrTriggeredLen) == '\\'){
					i = futureIndex;				
					//System.out.println("commandStrTriggered: " + commandStrTriggered + " thmSB: " + thmSB + " thmStr "+thmStr);
					thmSB.delete(thmSBLen - commandStrTriggeredLen, thmSBLen); 
					thmSB.append(commandStrSB);
					trieNodeList = new ArrayList<MacrosTrieNode>();
				}
				commandStrSB.setLength(0);
			}
		}
		return thmSB.toString();
	}

	/**
	 * Return how many places to skip ahead. Look for args inside braces {...}.
	 * Note that some authors don't explicitly use braces, e.g. $\\rr d$ instead of $\\rr{d}$.
	 * @param thmStr
	 * @param curIndex
	 * @param replacementSB SB to be filled in.
	 * @param checkNested: whether to check for nested macros.
	 * @return updated index (in original thmStr) to start further examination at.
	 */
	private int formReplacementString(String thmStr, int curIndex, MacrosTrieNode trieNode, StringBuilder replacementSB,
			boolean checkNested) {
		//replace #...		
		//int indexToSkip = 0;
		int slotCount = trieNode.slotCount;
		String templateReplacementString = trieNode.replacementStr;
		if(slotCount == 0){
			//some macros contain nested macros.
			if(checkNested) {
				templateReplacementString = replaceMacrosInThmStr(templateReplacementString, false);
			}
			replacementSB.append(templateReplacementString);
			//System.out.println("replacementSB " + templateReplacementString);
			return curIndex;
		}
		
		String[] args = new String[slotCount];
		
		//capture the arguments, fill in args in the next few braces, one at a time; 
		//e.g. \cmd{1}{2}
		int startingIndex = curIndex;
		
		//if optional argument not specified (i.e. use default), make slot count lower
		int toSlotCount = slotCount;
		//e.g. \cmd[optVal]{b}
		if(null != trieNode.optArgVal && thmStr.charAt(startingIndex+1) != '[') {
			toSlotCount--;
		}
		for(int j = 0; j < toSlotCount; j++){
			startingIndex = retrieveBracesContent(thmStr, startingIndex, args, j);			
		}
		if(toSlotCount < slotCount) {
			//shift all args in args one down, if optional arg default was supplied at macro definition
			//String[] tempArgs = new String[slotCount+1];			
			for(int k = slotCount-1; k > 0; k--) {
				args[k] = args[k-1];
			}
			args[0] = trieNode.optArgVal;
		}
		
		//System.out.println("+++++++++macros replacement args: " + Arrays.toString(args));
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
					logger.info("latex syntax error: slotArgNum >= slotCont || slotArgNum < 1 for thm: " + thmStr);
					continue;
				}				
				replacementSB.append(args[slotArgNum]);
				//System.out.println("args[slotArgNum] " + args[slotArgNum]);
				i++;
				lastSlotEndIndex = i;
				
			}else{
				replacementSB.append(c);				
			}
		}
		if(lastSlotEndIndex < templateReplacementStringLen-1){
			replacementSB.append(templateReplacementString.charAt(templateReplacementStringLen-1));
		}
		//*Uncomment this if need to replace content in nested macros.
		 //* //some macros contain nested macros.
		/*
		if(checkNested){
		String deepToStr = replaceMacrosInThmStr(replacementSB.toString());
		replacementSB.setLength(0);
		replacementSB.append(deepToStr);
		}*/
		
		//System.out.println("replacementSB " + replacementSB + " templateReplacementString "+ templateReplacementString);
		if(slotCount > 0 && startingIndex > curIndex){
			//to counter the i++ in the loop, but only if there is nontrivial parameter to command.
			startingIndex--;
		}
		return startingIndex;
	}

	/**
	 * Retrieves the content in braces down the stream, to fill up slots for replacing
	 * #1 in macros.
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
		//optional arg comes in brackets [
		while(i < thmStrLen && ((c=thmStr.charAt(i)) != '{') && c != '['){
			//Because some authors don't explicitly use braces, e.g. $\\rr d$ instead of $\\rr{d}$.
			if(c == '$' && i > index && thmStr.charAt(i-1) != '\\') {
				int j = i-1;
				while(j > index && thmStr.charAt(j) == ')') {
					j--;
				}
				//ugly code to take care of bad TeX.
				if(j < i-1) {
					bracesArgs[bracesArgsIndex] = thmStr.substring(index+1, j+1);
				}else {
					bracesArgs[bracesArgsIndex] = thmStr.substring(index+1, i);
				}
				return i;				
			}
			i++;
		}
		//skip brace, so openBraceCount is 1
		i++;
		int openBraceCount = 1;
		if(i >= thmStrLen){
			return index;
		}		
		char prevChar = ' ';
		StringBuilder braceContentSB = new StringBuilder(15);
		
		while(i < thmStr.length() && (((c=thmStr.charAt(i)) != '}' && c != ']') || prevChar == '\\' || openBraceCount>0 ) ){
			/* if braces don't all match, compile error. Unless escaped.*/	
			if(prevChar != '\\'){
				if(c == '{' || c == '['){
					openBraceCount++;
				}else if(c == '}' || c == ']'){
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
