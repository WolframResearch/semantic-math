package thmp.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.parse.ParseState;

/**
 * Turns Tex to tree of symbols, to 
 * facilitate variable selection in Tex,
 * for picking out contextual definitions.
 * @author yihed
 */
public class TexToTree {

	private static final Logger logger = LogManager.getLogger(TexToTree.class);
	//triggers used to descend into or ascend out of levels. Symbols here must be symmetric
	//ie every opening has a closing
	private static final Pattern DESCEND_PATTERN = Pattern.compile("[(\\[{]");
	private static final Pattern ASCEND_PATTERN = Pattern.compile("[)\\]}]");
	private static final String DESCEND_STRING = "([{";
	private static final String ASCEND_STRING = ")]}";
	
	
	public static class TexNode{
		
		//f[(x)(y)]
		private List<String> curLevelVars;
		private List<TexNode> children;
		
		public TexNode(List<String> curLevelVars_, List<TexNode> children_){
			this.curLevelVars = curLevelVars_;
			this.children = children_;
		}
		
		public TexNode(){
			this.curLevelVars = new ArrayList<String>();
			this.children = new ArrayList<TexNode>();
		}
		
		List<TexNode> children(){			
			return children;
		}
		
		List<String> curLevelVars(){
			return curLevelVars;
		}	
		
		void addChildNode(TexNode child){
			this.children.add(child);
		}
		void addVar(String var){
			this.curLevelVars.add(var);
		}
	}
	
	public static List<String> texToTree(String tex){
		TexNode rootNode = new TexNode();
		List<String> varsList = new ArrayList<String>();
		attachSymbols(tex, 0, rootNode, varsList);
		return varsList;
	}
	
	/**
	 * Attach symbols, either at current level, or at children
	 * levels. Starting at index index in the String tex. Enter new children
	 * levels if symbols such as parentheses, '(', are encountered.
	 * @param tex
	 * @param index
	 * @param parentNode
	 */
	private static int attachSymbols(String tex, int index, TexNode curNode,
			List<String> varsList){
		
		int texLen = tex.length();
		int i;
		for(i = index; i < texLen; i++){
			//char iChar = ;
			String iCharStr = String.valueOf(tex.charAt(i));
			if(iCharStr.equals(" ")){
				continue;
			}
			if(WordForms.getTexCommandBeginPattern().matcher(iCharStr).matches()){
				StringBuilder varSB = new StringBuilder();
				while(i+1 < texLen && (!WordForms.getTexCommandEndPattern().matcher((iCharStr=String.valueOf(tex.charAt(i+1)))).matches()
						//|| iCharStr.equals(" ")
						)){
					varSB.append(iCharStr);
					i++;
				}
				while(++i < texLen && iCharStr.equals(" ")){
					iCharStr = String.valueOf(tex.charAt(i));
				}
				//if(true) System.out.println("TexToTree varSb - "+varSB.toString());
				//look for Greek alphabets, which could be variables
				String varStr = varSB.toString();
				if(WordForms.isGreekAlpha(varStr)){
					varsList.add(varStr);
				}else if("mathbb".equals(varStr)){
					//if(varStr.c);
					varStr = varSB.append(ParseState.getNextParenthesizedToken(tex, i)).toString();
					varsList.add(varStr);
				}
				//iCharStr = String.valueOf(tex.charAt(i));
			}
			if(!WordForms.SPECIAL_CHARS_PATTERN.matcher(iCharStr).matches()){
				//for S_p, add S, p, and S_p, 
				char nextChar;
				if(i+1 < texLen && (nextChar=tex.charAt(i+1)) == '_'){
					StringBuilder varSB = new StringBuilder(iCharStr);//.append(nextChar);					
					boolean includeParen = false;
					i++;
					
					if(i+1 < texLen && DESCEND_STRING.contains(tex.substring(i+1, i+2))
							//DESCEND_PATTERN.matcher(String.valueOf((nextChar=tex.charAt(i+1)))).matches()
							){
						includeParen = true;
						varSB.append(nextChar);
						i++;
					}
					int newIndex = extractSubNodes(tex, varsList, i);
					System.out.println("newIndex " + includeParen);
					if(newIndex == texLen && !ASCEND_STRING.contains(tex.substring(texLen-1)) && !includeParen){
						//add 1 back if end of string encountered, but no parentheses when descending down. e.g. "S_A"
						newIndex++;
						if(true) throw new RuntimeException();
					}
					newIndex = includeParen ? newIndex : newIndex-1;
					//int startIndex = includeParen ? i+1 : i;
					for(int j = i; j < newIndex && j < texLen; j++){
						varSB.append(tex.charAt(j));
					}
					String var = varSB.toString();
					curNode.addVar(var);
					varsList.add(var);
					i = newIndex;
				}//else{
				curNode.addVar(iCharStr);
				varsList.add(iCharStr);
				//}
			}else if(DESCEND_PATTERN.matcher(iCharStr).matches()){
				i = extractSubNodes(tex, varsList, i);
			}
			//letter, not special symbol, so likely to be variable. Need to detect multi-char variables!
			else if(ASCEND_PATTERN.matcher(iCharStr).matches()){
				return i+1;
			}
		}		
		return i;
	}

	/**
	 * @param tex
	 * @param varsList
	 * @param i
	 * @return
	 */
	private static int extractSubNodes(String tex, List<String> varsList, int i) {
		TexNode childNode = new TexNode();
		i = attachSymbols(tex, i+1, childNode, varsList);
		return i;
	}
	
	public static String DESCEND_STRING(){
		return DESCEND_STRING;
	}
	
	public static String ASCEND_STRING(){
		return ASCEND_STRING;
	}
	
	public static void main(String[] args){
		String tex = "f(x \\Hom_+\" X() \\mathbb{y}) a";
		tex = "S_{A}";
		tex = "x \\oplus y";
		tex = "f[\\alpha] = y";
		tex = "X\\subset Y";
		tex = "\\mathbb{x}";
		System.out.println(texToTree(tex));
	}
	
}
