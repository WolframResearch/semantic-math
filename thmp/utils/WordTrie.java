package thmp.utils;

import java.util.Map;

import thmp.utils.WordTrie.WordTrieNode;

public abstract class WordTrie<T extends WordTrieNode>{
	
	public static abstract class WordTrieNode {
		
		/*public WordTrieNode getNode(char c){
			return nodeMap().get(c);
		}*/		
		
		//public abstract <T extends WordTrieNode> T addNode(char c, T node);
		/*public WordTrieNode addNode(char c, WordTrieNode node){
			Map<Character, ? extends WordTrieNode> nodeMap = nodeMap();
			if(nodeMap.containsKey(c)){
				
				return nodeMap.get(c);
			}else{
				//WordTrieNode trieNode = new WordTrieNode();
				//nodeMap.put(c, node);
			}
			return null;
		}*/		
		//public abstract Map<Character, ? extends WordTrieNode> nodeMap();
		
		//public abstract void addToNodeMap(WordTrieNode node);
		
	}
	
	public void addTrieNode(char c, WordTrieNode node){
		
	}
	
	
}
