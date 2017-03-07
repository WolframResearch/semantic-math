package thmp.utils;

import java.util.Map;

import thmp.utils.WordTrie.WordTrieNode;

/**
 * Trie for custom macros commands.
 * \newcommand{\xra}  {\xrightarrow}
 * \newcommand{\\un}[1]{\\underline{#1}}
 * @author yihed
 *
 */
public class MacrosTrie<MacrosTrieNode> extends WordTrie<WordTrieNode>{

	MacrosTrieNode rootNode;
	//WordTrie<MacrosTrieNode> trie = new MacrosTrie();
	
	public static class MacrosTrieNode extends WordTrieNode{
		//e.g. \xra
		String commandStr;
		//e.g. \xrightarrow
		String replacementStr;
		Map<Character, MacrosTrieNode> nodeMap;
		
		public MacrosTrieNode(String commandStr_, String replacementStr_){
			this.commandStr = commandStr_;
			this.replacementStr = replacementStr_;
		}
		
		public MacrosTrieNode getNode(char c){
			return nodeMap.get(c);
		}		
		
		/*@Override
		public Map<Character, MacrosTrieNode> nodeMap(){
			return this.nodeMap;
		}*/	
		
	}
	
	public MacrosTrie( ){
		
	}
	
	public void addTrieNode(char c, WordTrieNode node){
		
	}
	
}
