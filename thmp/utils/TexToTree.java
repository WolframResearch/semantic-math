package thmp.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
	private static final Pattern COMMAND_BEGIN_PATTERN = Pattern.compile("\\\\");
	//indicates termination of a Latex command
	private static final Pattern COMMAND_END_PATTERN = Pattern.compile("[\\$(\\[{\\])}_;,:!'`~%.\\-\"\\s]");
	private static final Set<String> GREEK_ALPHA_SET;
	
	static{
		GREEK_ALPHA_SET = new HashSet<String>();
		String[] GREEK_ALPHA = new String[]{"\\alpha"};
		for(String s : GREEK_ALPHA){
			GREEK_ALPHA_SET.add(s);
		}
		
	}
	
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
			if(COMMAND_BEGIN_PATTERN.matcher(iCharStr).matches()){
				StringBuilder varSB = new StringBuilder(10);
				while(++i < texLen && (!COMMAND_END_PATTERN.matcher((iCharStr=String.valueOf(tex.charAt(i)))).matches()
						|| iCharStr.equals(" "))){
					varSB.append(iCharStr);
				}
				//process first
				f
				//look for Greek alphabets, which could be variables
				String varStr = varSB.toString();
				if(GREEK_ALPHA_SET.contains(varStr)){
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
	
	/**
	 * Set of Greek alphabets
	 * @return
	 */
	public static Set<String> GREEK_ALPHA_SET(){
		return GREEK_ALPHA_SET;
	}
	
	public static void main(String[] args){
		String tex = "f(x \\Hom_+\" X() \\mathbb{y}) a";
		tex = "S_{A}";
		tex = "x \\oplus y";
		
		System.out.println(texToTree(tex));
	}
	
}
